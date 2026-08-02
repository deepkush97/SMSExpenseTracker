# Logging & Backup (Task 8) — Design Spec

**Date:** 2026-08-02
**Status:** Approved
**Task reference:** TODO.md Task 8 (Infrastructure: Logging & Backup)

## 1. Overview

Task 8 adds the app's infrastructure layer: file-based logging (Timber tree + crash handler writing to 4 log files) and CSV export/import (backup/restore of all transactions). This also wires the Settings rows that Task 14 still lists as unchecked: Export CSV, Import CSV, and Logs.

## 2. Goals

- `FileLogger`: append to `filesDir/logs/{error_log, parse_failures, unparsed_sms, crash_log}.txt`; read back; clear; expose FileProvider Uri for sharing.
- `FileLoggingTree` (Timber): routes priority/tag to the right log file; planted in `SmsExpenseApp.onCreate` alongside `DebugTree`.
- Crash handler: `Thread.setDefaultUncaughtExceptionHandler` writes the stack trace to `crash_log.txt`, then re-throws to the previous handler.
- CSV export: all transactions → `filesDir/exports/transactions_<ts>.csv` → share sheet (ACTION_SEND via FileProvider).
- CSV import: SAF `ACTION_OPEN_DOCUMENT` picker → parse, validate rows, dedup by `(amount, transactionDate, description)`, bulk insert.
- Log viewer screen (Settings → Logs): File Logs section (4 files, share/clear each) + Parse Log section (DB-backed parse results).
- Settings "Data" section gains Export CSV / Import CSV / Logs rows.

## 3. Non-Goals

- Incremental/background sync, WorkManager (separate sub-project).
- About-section polish (app version beyond the existing static text).
- Per-rule log filtering, log retention/size caps (log files grow unbounded; a future cleanup pass can add rotation).
- CSV of banks/categories/rules (transactions only).
- Import auto-category assignment.

## 4. Architecture

Follows the existing split: pure logic in `core/` (JVM-testable, no Android), Android I/O in `data/`, use cases in `domain/`.

```
core/csv/        CsvCodec.kt            pure RFC-4180 encode/parse + row mapping
data/logging/    FileLogger.kt          @Singleton; 4 named log files; injectable base dir
                 FileLoggingTree.kt     Timber.DebugTree → FileLogger; priority/tag routing
data/csv/        CsvExporter.kt         transactions → CSV file → content Uri
                 CsvImporter.kt         content Uri → parse/validate/dedup → insertBatch
domain/usecase/  ExportCsvUseCase.kt    replaces stub; returns ExportResult
                 ImportCsvUseCase.kt    returns ImportResult
ui/screens/logs/ LogViewerScreen.kt     two sections: File Logs + Parse Log
                 LogViewerViewModel.kt
di/              LoggingModule.kt       provides FileLogger (base dir = filesDir), LoggingSetup
                 CsvModule.kt           provides CsvExporter, CsvImporter
```

**Testability decision:** `FileLogger`, `CsvExporter`, `CsvImporter` take an injectable **base-dir `File`** (default `context.filesDir` via Hilt provider) instead of hardcoding `Context.filesDir` internally. Tests use JUnit `@TempDir` with no Android dependency. Only FileProvider/ContentResolver code stays Android-bound.

## 5. CsvCodec (`core/csv/CsvCodec.kt`, pure)

```kotlin
object CsvCodec {
    fun encode(rows: List<List<String>>): String
    fun parse(text: String): List<List<String>>        // throws IllegalArgumentException on malformed input
    fun toCsv(transactions: List<Transaction>): String  // header + rows; amount as paisa string; date ISO
    fun fromCsv(text: String): List<CsvTransactionRow>  // header-aware; paisa → Long; date → LocalDateTime
}
data class CsvTransactionRow(
    val amount: Long, val type: TransactionType, val description: String,
    val transactionDate: LocalDateTime, val bankId: Long?, val categoryId: Long?,
    val smsTimestamp: Long, val parseMethod: ParseMethod, val rawSms: String
)
```

- RFC-4180 quoting: any field containing `,`, `"`, CR, or LF is wrapped in quotes; embedded quotes doubled.
- Header: `date,amount,type,description,bankId,categoryId,smsTimestamp,parseMethod,rawSms`.
- Amount serialized as plain paisa long (`125050`), not `₹1,250.50`.
- Date serialized ISO `2026-08-02T10:00:00`; parsed back via `LocalDateTime.parse`; type/parseMethod parsed from enum names; unknown/missing values for `bankId`/`categoryId`/`rawSms`/`smsTimestamp` default safely (null/0/"").
- Malformed CSV structure (unterminated quote, wrong header) → `IllegalArgumentException` with a readable message.

## 6. FileLogger (`data/logging/FileLogger.kt`)

`@Singleton`, constructor `FileLogger(baseDir: File)` where baseDir defaults to `filesDir` via Hilt.

```kotlin
enum class LogFile { ERROR_LOG, PARSE_FAILURES, UNPARSED_SMS, CRASH_LOG }

suspend fun append(file: LogFile, line: String)          // withContext(Dispatchers.IO)
suspend fun read(file: LogFile): String                  // "" if missing
suspend fun readAll(): Map<LogFile, String>
suspend fun clear(file: LogFile)
fun logFileName(file: LogFile): String                   // error_log.txt etc.
fun logFileUri(file: LogFile): Uri                       // FileProvider Uri (sharing)
```

- Append format: `[yyyy-MM-dd HH:mm:ss] <line>\n`; dir + file created on demand.
- File name mapping: `error_log.txt`, `parse_failures.txt`, `unparsed_sms.txt`, `crash_log.txt`.
- `logFileUri` uses FileProvider; called on the main thread (Uri construction is cheap; path is under `filesDir`).

## 7. FileLoggingTree + crash handler (`data/logging/`)

`class FileLoggingTree(private val logger: FileLogger) : Timber.DebugTree()`:
- Overrides `log(priority, tag, message, t)`. Routing:
  - priority `ERROR` or `WARN` → `ERROR_LOG`
  - tag `"PARSE"` → `PARSE_FAILURES`
  - tag `"UNPARSED"` → `UNPARSED_SMS`
  - default (INFO/DEBUG/VERBOSE) → no file write (DebugTree handles console)
- Call sites: `Timber.tag("PARSE").w("...")`, `Timber.tag("UNPARSED").w("...")`, `Timber.e(...)` for errors.

**`LoggingSetup`** — a small Hilt-injected class (`@Singleton`) with `fun install()` that:
1. Plants `DebugTree` (if not already) + `FileLoggingTree(fileLogger)`.
2. Installs `Thread.setDefaultUncaughtExceptionHandler` wrapping the previous handler: writes the Throwable stack trace to `CRASH_LOG`, then delegates to the previous handler.

**`SmsExpenseApp.onCreate`**: `@Inject lateinit var loggingSetup: LoggingSetup`; call `loggingSetup.install()` in `onCreate` (before `demoDataSeeder` seed or after — no logging dependency between them; run install first for consistency). The single FileLogger instance is provided by Hilt; no duplicate construction.

**SmsSyncUseCase integration (optional, minimal):** when a parse fails during sync, additionally `Timber.tag("PARSE").w(...)` / for blank/unparsed `Timber.tag("UNPARSED").w(...)` — alongside the existing `parseLogRepository.insert`. This is a small change in `SmsSyncUseCase.sync()` at the two failure points.

## 8. CsvExporter (`data/csv/CsvExporter.kt`)

`@Singleton`, constructor `(baseDir: File, private val transactionRepository: TransactionRepository)`.

```kotlin
data class ExportResult(val uri: Uri, val fileName: String, val count: Int)

suspend fun exportAll(): ExportResult
```

- `getAllTransactions().first()` → `CsvCodec.toCsv` → write `baseDir/exports/transactions_yyyyMMdd_HHmmss.csv`.
- Return `FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)`.
- Empty data → header-only CSV (count 0), still valid.

## 9. CsvImporter (`data/csv/CsvImporter.kt`)

`@Singleton`, constructor `(@ApplicationContext context: Context, baseDir: File, transactionRepository: TransactionRepository)`.

```kotlin
data class ImportResult(val imported: Int, val skipped: Int, val invalid: Int)

suspend fun importFrom(uri: Uri): ImportResult
```

- Read text from `uri` via ContentResolver InputStream.
- `CsvCodec.fromCsv(text)` — throws on malformed → use case returns `Result.failure`.
- Map rows → `Transaction` candidates; row-level validation: amount > 0, `transactionType` present, date parseable. Invalid rows → `invalid++`, continue.
- Dedup: load `getAllTransactions().first()`, build `Set<Triple<Long, LocalDateTime, String>>` of `(amount, transactionDate, description)`; skip matching rows → `skipped++`.
- `insertBatch(validNew)` once → `imported = returned count`.
- Note: `CsvImporter` reads the stream but does not itself hold the picker Uri across rotations — the ViewModel/screen passes the Uri in.

## 10. Use cases (`domain/usecase/`)

```kotlin
@Singleton class ExportCsvUseCase @Inject constructor(private val exporter: CsvExporter) {
    suspend operator fun invoke(): Result<ExportResult>
}
@Singleton class ImportCsvUseCase @Inject constructor(private val importer: CsvImporter) {
    suspend operator fun invoke(uri: Uri): Result<ImportResult>
}
```

- Delete `ExportCsvUseCase` stub (the old no-arg one). Replace with the above.
- Both wrap data-layer calls in try/catch, rethrow `CancellationException`, map other exceptions to `Result.failure`.

## 11. Log viewer (`ui/screens/logs/`)

**Route:** `"logs"` in NavGraph → `LogViewerScreen(onBack = { navController.popBackStack() })`.

**`LogViewerViewModel`** — `@HiltViewModel`, injects `ParseLogRepository` + `FileLogger`:
- `val parseLogs: StateFlow<List<ParseLog>>` — `getAllLogs()` → `stateIn(viewModelScope, WhileSubscribed(5000), emptyList())`.
- `val fileLogs: StateFlow<Map<LogFile, String>>` — initially empty; `fun refresh()` loads `logger.readAll()`.
- `fun clearFile(file: LogFile)` — `logger.clear(file)` then refresh that file.
- `fun refresh()` — reload `fileLogs`.

**`LogViewerScreen`** — `Scaffold` + TopAppBar "Logs" + back. `LazyColumn`:
1. **File Logs** section: for each `LogFile` a Card with title, a monospace body (last ~200 lines, truncate long bodies), and `Share` + `Clear` actions. Share: `context.startActivity(ACTION_SEND, EXTRA_STREAM = logger.logFileUri(file), type = "text/plain")` with `ActivityNotFoundException` guard → snackbar. Clear: confirm dialog → `viewModel.clearFile(file)`.
2. **Parse Log** section: newest-first list of `ParseLog` rows: timestamp, smsSender, status badge (SUCCESS/FAILED/SKIPPED), errorMessage when present.

## 12. Settings wiring (`ui/screens/settings/`)

Add to the "Data" section (after Banks & Rules row):

- **Export CSV** row (icon e.g. `Icons.Filled.Share`): `SettingsViewModel.exportCsv()` → `ExportCsvUseCase` → on success build `ACTION_SEND` with `result.uri` + text/plain, start via `context.startActivity`; snackbar "Exported N transactions" / failure.
- **Import CSV** row (icon e.g. `Icons.Filled.FileOpen`): launch SAF `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())` with `arrayOf("text/csv", "text/comma-separated-values", "text/plain")`; on result `SettingsViewModel.importCsv(uri)` → `ImportCsvUseCase` → snackbar "Imported N, skipped M, invalid K" / failure.
- **Logs** row (icon e.g. `Icons.Filled.Description`): `onNavigateToLogs` → NavGraph `"logs"`.

**`SettingsViewModel`** additions: `uiState` gains `csvMessage: String?` (snackbar), `isCsvBusy: Boolean`; functions `exportCsv()`, `importCsv(uri)`, `consumeCsvMessage()`. ViewModel gets `@Inject` for `ExportCsvUseCase` + `ImportCsvUseCase`. Uses `viewModelScope.launch`; sets `isCsvBusy` around the call to prevent double-tap.

**SettingsScreen signature:** add `onNavigateToLogs: () -> Unit = {}`. NavGraph passes `navController.navigate("logs")`.

## 13. DI

- `di/LoggingModule.kt`: `@Provides @Singleton fun provideFileLogger(@ApplicationContext context: Context): FileLogger = FileLogger(context.filesDir)`; `@Provides @Singleton fun provideLoggingSetup(fileLogger: FileLogger): LoggingSetup`.
- `di/CsvModule.kt`: `@Provides @Singleton fun provideCsvExporter(baseDir: File, transactionRepository: TransactionRepository): CsvExporter` — baseDir from `context.filesDir`; `@Provides @Singleton fun provideCsvImporter(@ApplicationContext context: Context, transactionRepository: TransactionRepository): CsvImporter`.
- Use cases need no module (constructor-injectable).

## 14. Manifest & resources

- `AndroidManifest.xml`: `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">` with `<meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>`.
- `res/xml/file_paths.xml`: `<files-path name="exports" path="exports/"/>` and `<files-path name="logs" path="logs/"/>`.

## 15. Testing

- `CsvCodecTest` (JUnit 4, Parameterized): encode round-trip; RFC-4180 quoting (embedded comma/quote/newline); parse of quoted+multi-line fields; malformed input throws; `toCsv`/`fromCsv` round-trip with paisa+date+enum; header-mismatch throws; tolerant missing optional fields.
- `FileLoggerTest` (`@TempDir`): append creates dir+file; append format `[timestamp] line`; read missing file → ""; read round-trip; clear truncates; file naming.
- `FileLoggingTreeTest` (`@TempDir`): ERROR/WARN → error_log; tag "PARSE" → parse_failures; tag "UNPARSED" → unparsed_sms; default/DEBUG → no file.
- `CsvExporterTest` (`@TempDir`, mock `TransactionRepository`): export writes file; header-only on empty; returns Uri + count; file under baseDir/exports.
- `CsvImporterTest` (`@TempDir`, mock repo): happy path inserts; invalid rows counted not fatal; dedup skips existing; malformed CSV → exception. To keep the test on the JVM, `CsvImporter` splits the work: a constructor-injected `ContentResolver` handles only the real-`Uri` read (thin), while a package-visible `fun importFromText(text: String): ImportResult` holds all parse/validate/dedup/insert logic. Tests exercise `importFromText` with strings; the resolver path is a 3-line wrapper covered by smoke.
- `ExportCsvUseCaseTest` / `ImportCsvUseCaseTest` (MockK): success + failure (repo throws → Result.failure; CancellationException rethrown).
- `LogViewerViewModelTest` (MockK + runTest): parseLogs flow emission; refresh populates fileLogs; clearFile calls logger + refresh.
- `SettingsViewModelTest` (MockK + runTest): exportCsv success sets message; importCsv success/failure messages; isCsvBusy gating; consumeCsvMessage.
- `SmsSyncUseCaseTest`: update existing failure-path tests if `SmsSyncUseCase` gains `Timber` calls — verify the DB insert still happens (Timber calls must not break existing assertions). Timber calls in tests: use `Timber.Forest` with a no-op or plant nothing (Timber without planted trees is a no-op safe to call).
- Migration/schema: none (no DB change).

## 16. Files

**New:**
- `core/csv/CsvCodec.kt` + `core/csv/CsvTransactionRow.kt`
- `data/logging/FileLogger.kt`, `data/logging/FileLoggingTree.kt`, `data/logging/LoggingSetup.kt`
- `data/csv/CsvExporter.kt`, `data/csv/CsvImporter.kt`
- `domain/usecase/ExportCsvUseCase.kt` (rewrite), `domain/usecase/ImportCsvUseCase.kt`
- `ui/screens/logs/LogViewerScreen.kt`, `ui/screens/logs/LogViewerViewModel.kt`
- `di/LoggingModule.kt`, `di/CsvModule.kt`
- `res/xml/file_paths.xml`
- Tests: `CsvCodecTest`, `FileLoggerTest`, `FileLoggingTreeTest`, `CsvExporterTest`, `CsvImporterTest`, `ExportCsvUseCaseTest`, `ImportCsvUseCaseTest`, `LogViewerViewModelTest`, `SettingsViewModelTest` additions

**Modified:**
- `AndroidManifest.xml` (FileProvider)
- `SmsExpenseApp.kt` (install LoggingSetup)
- `domain/usecase/SmsSyncUseCase.kt` (optional Timber tags on parse failure)
- `ui/screens/settings/SettingsScreen.kt` (3 rows + picker + share)
- `ui/screens/settings/SettingsViewModel.kt` (export/import/logs state)
- `ui/navigation/NavGraph.kt` (`"logs"` route, `onNavigateToLogs`)
- `TODO.md`

## 17. Verification

- `./gradlew testDebugUnitTest assembleDebug` — all tests pass, build succeeds.
- `res/xml/file_paths.xml` + manifest provider present.
- Manual smoke: sync with an SMS that fails parsing → `parse_failures.txt` and `unparsed_sms.txt` populated; Settings → Logs shows both sections; Share a log file opens a share sheet; Clear empties it; Export CSV → share sheet → save → file opens in a spreadsheet app with all transactions; Import CSV (exported file) → "Imported N, skipped N"; import a malformed file → snackbar error; import same file twice → second import all-skipped.
