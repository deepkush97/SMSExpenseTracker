# Logging & Backup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the app's infrastructure layer — file-based logging (Timber tree + crash handler writing to 4 log files under `filesDir/logs/`), a log viewer screen (Settings → Logs), and CSV export/import (backup/restore of all transactions via FileProvider + SAF).

**Architecture:** Pure CSV encode/parse lives in `core/csv/CsvCodec.kt` (JVM-testable, imports `domain` models like the existing `core/parser/` does). Android I/O lives in `data/logging/` (FileLogger, FileLoggingTree, LoggingSetup) and `data/csv/` (CsvExporter, CsvImporter). Use cases wrap them in `domain/usecase/`. `FileLogger`/`CsvExporter`/`CsvImporter` take an injectable base-dir `File` (default `context.filesDir` via Hilt module) so JVM tests use `TemporaryFolder` — only FileProvider/ContentResolver code stays Android-bound. Screens `ui/screens/logs/` and Settings wiring complete the feature.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, Hilt, Room, Timber, MockK, `kotlinx-coroutines-test` (JUnit 4, `TemporaryFolder`).

## Global Constraints

- Package: `com.smsexpensetracker`; min SDK 28 / target 36 / compile 37.
- No code comments unless the user asks for them.
- All amounts as paisa `Long` — never `Double`/`BigDecimal`.
- MockK for mocks; `runTest` + `StandardTestDispatcher` for Flow/suspend; `Dispatchers.setMain` in `@Before`, `resetMain` in `@After`.
- Build gate: `./gradlew testDebugUnitTest assembleDebug` must be green before each commit. No `lint`/`typecheck` configured.
- Screens have no unit tests in this codebase (ViewModel/use case/data-layer tests only).
- Enum types come from `com.smsexpensetracker.domain.model` (`Transaction`, `TransactionType`, `ParseMethod`).
- FileProvider authority constant: `FILE_PROVIDER_AUTHORITY = "com.smsexpensetracker.fileprovider"` (matches `applicationId`; do NOT enable `buildConfig`).
- `CsvImporter` splits work: ContentResolver only for the real-`Uri` read; `internal fun importFromText(text: String)` holds all parse/validate/dedup/insert logic (JVM-testable).
- `CsvExporter` splits work: `internal suspend fun buildExportFile(): ExportFile` is the JVM-tested pure path; `exportAll()` wraps it with `FileProvider.getUriForFile`.
- Spec: `docs/superpowers/specs/2026-08-02-logging-backup-design.md`.

---

### Task 1: CsvCodec (pure core)

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/core/csv/CsvCodec.kt`
- Create: `app/src/main/java/com/smsexpensetracker/core/csv/CsvTransactionRow.kt`
- Test: `app/src/test/java/com/smsexpensetracker/core/csv/CsvCodecTest.kt`

**Interfaces:**
- Consumes: `com.smsexpensetracker.domain.model.Transaction`, `TransactionType`, `ParseMethod`.
- Produces:
  - `data class CsvTransactionRow(val amount: Long, val type: TransactionType, val description: String, val transactionDate: LocalDateTime, val bankId: Long?, val categoryId: Long?, val smsTimestamp: Long, val parseMethod: ParseMethod, val rawSms: String)`
  - `object CsvCodec`: `encode(rows: List<List<String>>): String`; `parse(text: String): List<List<String>>` (throws `IllegalArgumentException` on unterminated quote); `toCsv(transactions: List<Transaction>): String`; `fromCsv(text: String): List<CsvTransactionRow>` (throws on wrong header); `fun toTransactionRow(row: List<String>): CsvTransactionRow?` (null on conversion failure); `fun requireHeader(rows: List<List<String>>)` (throws on empty/wrong header); `val HEADER: List<String>`.

- [ ] **Step 1: Write the failing test**

Create `CsvCodecTest.kt`:

```kotlin
package com.smsexpensetracker.core.csv

import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CsvCodecTest {

    private val tx = Transaction(
        id = 1L,
        bankId = 2L,
        amount = 125050L,
        transactionType = TransactionType.DEBIT,
        description = "Acme Inc",
        transactionDate = LocalDateTime.of(2026, 8, 2, 10, 0, 0),
        categoryId = 3L,
        rawSms = "Spent Rs.1,250.50 at Acme On 02-08-26",
        smsTimestamp = 1750000000000L,
        createdAt = LocalDateTime.of(2026, 8, 2, 10, 0, 0),
        parseMethod = ParseMethod.SMS
    )

    @Test
    fun `encode and parse round-trip plain fields`() {
        val csv = CsvCodec.encode(listOf(listOf("a", "b"), listOf("1", "2")))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), CsvCodec.parse(csv))
    }

    @Test
    fun `encode quotes fields with comma quote newline`() {
        val csv = CsvCodec.encode(listOf(listOf("a,b", "say \"hi\"", "line1\nline2")))
        assertEquals("\"a,b\",\"say \"\"hi\"\"\",\"line1\nline2\"", csv)
    }

    @Test
    fun `parse quoted fields with embedded comma and quote`() {
        val rows = CsvCodec.parse("\"a,b\",\"say \"\"hi\"\"\"")
        assertEquals(listOf(listOf("a,b", "say \"hi\"")), rows)
    }

    @Test
    fun `parse handles crlf line endings`() {
        val rows = CsvCodec.parse("a,b\r\nc,d\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), rows)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on unterminated quote`() {
        CsvCodec.parse("\"unterminated")
    }

    @Test
    fun `toCsv uses paisa amount and iso date with header`() {
        val csv = CsvCodec.toCsv(listOf(tx))
        val rows = CsvCodec.parse(csv)
        assertEquals(
            listOf(
                "date", "amount", "type", "description", "bankId", "categoryId",
                "smsTimestamp", "parseMethod", "rawSms"
            ),
            rows.first()
        )
        assertEquals(
            listOf(
                "2026-08-02T10:00", "125050", "DEBIT", "Acme Inc", "2", "3",
                "1750000000000", "SMS", tx.rawSms
            ),
            rows[1]
        )
    }

    @Test
    fun `fromCsv round-trips transaction fields`() {
        val roundTripped = CsvCodec.fromCsv(CsvCodec.toCsv(listOf(tx))).single()
        assertEquals(125050L, roundTripped.amount)
        assertEquals(TransactionType.DEBIT, roundTripped.type)
        assertEquals("Acme Inc", roundTripped.description)
        assertEquals(LocalDateTime.of(2026, 8, 2, 10, 0, 0), roundTripped.transactionDate)
        assertEquals(2L, roundTripped.bankId)
        assertEquals(3L, roundTripped.categoryId)
        assertEquals(1750000000000L, roundTripped.smsTimestamp)
        assertEquals(ParseMethod.SMS, roundTripped.parseMethod)
        assertEquals(tx.rawSms, roundTripped.rawSms)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromCsv throws on wrong header`() {
        CsvCodec.fromCsv("foo,bar\n1,2")
    }

    @Test
    fun `fromCsv defaults missing optional fields`() {
        val csv = CsvCodec.encode(
            listOf(
                CsvCodec.HEADER,
                listOf("2026-08-02T10:00:00", "500", "CREDIT", "Refund", "", "", "", "SMS", "")
            )
        )
        val row = CsvCodec.fromCsv(csv).single()
        assertNull(row.bankId)
        assertNull(row.categoryId)
        assertEquals(0L, row.smsTimestamp)
        assertEquals("", row.rawSms)
    }

    @Test
    fun `toTransactionRow returns null for unparseable amount`() {
        assertNull(CsvCodec.toTransactionRow(listOf("2026-08-02T10:00:00", "abc", "DEBIT", "x", "1", "", "0", "SMS", "")))
    }

    @Test
    fun `toTransactionRow returns null for unknown enum`() {
        assertNull(CsvCodec.toTransactionRow(listOf("2026-08-02T10:00:00", "500", "NOPE", "x", "1", "", "0", "SMS", "")))
    }

    @Test
    fun `empty transactions produce header-only csv`() {
        val rows = CsvCodec.parse(CsvCodec.toCsv(emptyList()))
        assertEquals(1, rows.size)
        assertEquals(CsvCodec.HEADER, rows.first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.csv.CsvCodecTest"`
Expected: FAIL with "class CsvCodec not found" (unresolved reference).

- [ ] **Step 3: Write the implementation**

Create `CsvTransactionRow.kt`:

```kotlin
package com.smsexpensetracker.core.csv

import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.TransactionType
import java.time.LocalDateTime

data class CsvTransactionRow(
    val amount: Long,
    val type: TransactionType,
    val description: String,
    val transactionDate: LocalDateTime,
    val bankId: Long?,
    val categoryId: Long?,
    val smsTimestamp: Long,
    val parseMethod: ParseMethod,
    val rawSms: String
)
```

Create `CsvCodec.kt`:

```kotlin
package com.smsexpensetracker.core.csv

import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import java.time.LocalDateTime

object CsvCodec {

    val HEADER: List<String> = listOf(
        "date", "amount", "type", "description", "bankId", "categoryId",
        "smsTimestamp", "parseMethod", "rawSms"
    )

    fun encode(rows: List<List<String>>): String =
        rows.joinToString("\n") { row ->
            row.joinToString(",") { field -> escape(field) }
        }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    row.add(field.toString())
                    field.setLength(0)
                }
                c == '\n' -> {
                    row.add(field.toString())
                    field.setLength(0)
                    rows.add(row.toList())
                    row.clear()
                }
                c == '\r' -> Unit
                else -> field.append(c)
            }
            i++
        }
        if (inQuotes) throw IllegalArgumentException("Unterminated quoted field in CSV")
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row.toList())
        }
        return rows
    }

    fun requireHeader(rows: List<List<String>>) {
        if (rows.isEmpty()) throw IllegalArgumentException("CSV is empty")
        if (rows.first() != HEADER) throw IllegalArgumentException("Unexpected CSV header")
    }

    fun toCsv(transactions: List<Transaction>): String {
        val body = transactions.map { t ->
            listOf(
                t.transactionDate.toString(),
                t.amount.toString(),
                t.transactionType.name,
                t.description,
                t.bankId.toString(),
                t.categoryId?.toString() ?: "",
                t.smsTimestamp.toString(),
                t.parseMethod.name,
                t.rawSms
            )
        }
        return encode(listOf(HEADER) + body)
    }

    fun fromCsv(text: String): List<CsvTransactionRow> {
        val rows = parse(text)
        requireHeader(rows)
        return rows.drop(1).mapNotNull { toTransactionRow(it) }
    }

    fun toTransactionRow(row: List<String>): CsvTransactionRow? {
        if (row.size < HEADER.size) return null
        val amount = row[1].toLongOrNull() ?: return null
        val type = TransactionType.values().firstOrNull { it.name == row[2] } ?: return null
        val date = runCatching { LocalDateTime.parse(row[0]) }.getOrNull() ?: return null
        return CsvTransactionRow(
            amount = amount,
            type = type,
            description = row[3],
            transactionDate = date,
            bankId = row[4].toLongOrNull(),
            categoryId = row[5].toLongOrNull(),
            smsTimestamp = row[6].toLongOrNull() ?: 0L,
            parseMethod = ParseMethod.values().firstOrNull { it.name == row[7] } ?: ParseMethod.SMS,
            rawSms = row[8]
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.csv.CsvCodecTest"`
Expected: PASS (all 12 tests).

- [ ] **Step 5: Run the full test suite + build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: green (existing 215 + 12 new).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/csv app/src/test/java/com/smsexpensetracker/core/csv
git commit -m "feat(core): add CsvCodec with RFC-4180 encode/parse and transaction mapping"
```

---

### Task 2: FileLogger

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/data/logging/FileLogger.kt`
- Create: `app/src/main/java/com/smsexpensetracker/data/csv/FileProviderConfig.kt`
- Test: `app/src/test/java/com/smsexpensetracker/data/logging/FileLoggerTest.kt`

**Interfaces:**
- Consumes: `android.content.Context` (only for `logFileUri`), `androidx.core.content.FileProvider`, `FILE_PROVIDER_AUTHORITY` from `data/csv`.
- Produces:
  - `enum class LogFile { ERROR_LOG, PARSE_FAILURES, UNPARSED_SMS, CRASH_LOG }`
  - `@Singleton class FileLogger(private val context: Context, private val baseDir: File)` with:
    - `fun logFileName(file: LogFile): String`
    - `fun logFile(file: LogFile): File`
    - `fun appendBlocking(file: LogFile, line: String)` (synchronous; used by Timber tree + crash handler)
    - `suspend fun append(file: LogFile, line: String)`
    - `suspend fun read(file: LogFile): String`
    - `suspend fun readAll(): Map<LogFile, String>`
    - `suspend fun clear(file: LogFile)`
    - `fun logFileUri(file: LogFile): android.net.Uri`
  - `const val FILE_PROVIDER_AUTHORITY = "com.smsexpensetracker.fileprovider"` in `data/csv/FileProviderConfig.kt`.

- [ ] **Step 1: Write the failing test**

Create `FileLoggerTest.kt`:

```kotlin
package com.smsexpensetracker.data.logging

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FileLoggerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val logger by lazy { FileLogger(mockk<Context>(), tempDir.root) }

    @Test
    fun `logFileName maps each file`() = runTest {
        assertEquals("error_log.txt", logger.logFileName(LogFile.ERROR_LOG))
        assertEquals("parse_failures.txt", logger.logFileName(LogFile.PARSE_FAILURES))
        assertEquals("unparsed_sms.txt", logger.logFileName(LogFile.UNPARSED_SMS))
        assertEquals("crash_log.txt", logger.logFileName(LogFile.CRASH_LOG))
    }

    @Test
    fun `append creates dir and file with timestamped line`() = runTest {
        logger.append(LogFile.ERROR_LOG, "boom")
        val file = File(tempDir.root, "logs/error_log.txt")
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.matches(Regex("""^\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\] boom\n$""")))
    }

    @Test
    fun `appendBlocking writes synchronously`() {
        logger.appendBlocking(LogFile.CRASH_LOG, "crash")
        val content = File(tempDir.root, "logs/crash_log.txt").readText()
        assertTrue(content.contains("crash"))
    }

    @Test
    fun `read missing file returns empty string`() = runTest {
        assertEquals("", logger.read(LogFile.PARSE_FAILURES))
    }

    @Test
    fun `read round-trips appended content`() = runTest {
        logger.append(LogFile.ERROR_LOG, "first")
        logger.append(LogFile.ERROR_LOG, "second")
        val content = logger.read(LogFile.ERROR_LOG)
        assertTrue(content.contains("first"))
        assertTrue(content.contains("second"))
    }

    @Test
    fun `readAll returns all four files`() = runTest {
        logger.append(LogFile.ERROR_LOG, "e")
        logger.append(LogFile.CRASH_LOG, "c")
        val all = logger.readAll()
        assertEquals(4, all.size)
        assertTrue(all.getValue(LogFile.ERROR_LOG).contains("e"))
        assertTrue(all.getValue(LogFile.CRASH_LOG).contains("c"))
        assertEquals("", all.getValue(LogFile.UNPARSED_SMS))
    }

    @Test
    fun `clear truncates the file`() = runTest {
        logger.append(LogFile.ERROR_LOG, "data")
        logger.clear(LogFile.ERROR_LOG)
        assertEquals("", logger.read(LogFile.ERROR_LOG))
    }

    @Test
    fun `logFile is under baseDir logs dir`() {
        assertEquals(File(tempDir.root, "logs/error_log.txt"), logger.logFile(LogFile.ERROR_LOG))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.logging.FileLoggerTest"`
Expected: FAIL (unresolved `FileLogger`).

- [ ] **Step 3: Write the implementation**

Create `data/csv/FileProviderConfig.kt`:

```kotlin
package com.smsexpensetracker.data.csv

const val FILE_PROVIDER_AUTHORITY = "com.smsexpensetracker.fileprovider"
```

Create `data/logging/FileLogger.kt`:

```kotlin
package com.smsexpensetracker.data.logging

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.smsexpensetracker.data.csv.FILE_PROVIDER_AUTHORITY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class LogFile { ERROR_LOG, PARSE_FAILURES, UNPARSED_SMS, CRASH_LOG }

@Singleton
class FileLogger @Inject constructor(
    private val context: Context,
    private val baseDir: File
) {

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun logFileName(file: LogFile): String = when (file) {
        LogFile.ERROR_LOG -> "error_log.txt"
        LogFile.PARSE_FAILURES -> "parse_failures.txt"
        LogFile.UNPARSED_SMS -> "unparsed_sms.txt"
        LogFile.CRASH_LOG -> "crash_log.txt"
    }

    fun logFile(file: LogFile): File = File(File(baseDir, "logs"), logFileName(file))

    fun appendBlocking(file: LogFile, line: String) {
        val target = logFile(file)
        target.parentFile?.mkdirs()
        val timestamp = LocalDateTime.now().format(timestampFormat)
        target.appendText("[$timestamp] $line\n", Charsets.UTF_8)
    }

    suspend fun append(file: LogFile, line: String) = withContext(Dispatchers.IO) {
        appendBlocking(file, line)
    }

    suspend fun read(file: LogFile): String = withContext(Dispatchers.IO) {
        val target = logFile(file)
        if (target.exists()) target.readText(Charsets.UTF_8) else ""
    }

    suspend fun readAll(): Map<LogFile, String> = withContext(Dispatchers.IO) {
        LogFile.entries.associateWith { file ->
            val target = logFile(file)
            if (target.exists()) target.readText(Charsets.UTF_8) else ""
        }
    }

    suspend fun clear(file: LogFile) = withContext(Dispatchers.IO) {
        logFile(file).writeText("", Charsets.UTF_8)
    }

    fun logFileUri(file: LogFile): Uri =
        FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, logFile(file))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.logging.FileLoggerTest"`
Expected: PASS (all 8 tests).

- [ ] **Step 5: Run the full test suite + build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/data/logging/FileLogger.kt app/src/main/java/com/smsexpensetracker/data/csv/FileProviderConfig.kt app/src/test/java/com/smsexpensetracker/data/logging/FileLoggerTest.kt
git commit -m "feat(data): add FileLogger with 4 log files and FileProvider uris"
```

---

### Task 3: FileLoggingTree + LoggingSetup + DI + app wiring

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/data/logging/FileLoggingTree.kt`
- Create: `app/src/main/java/com/smsexpensetracker/data/logging/LoggingSetup.kt`
- Create: `app/src/main/java/com/smsexpensetracker/di/LoggingModule.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/SmsExpenseApp.kt`
- Test: `app/src/test/java/com/smsexpensetracker/data/logging/FileLoggingTreeTest.kt`

**Interfaces:**
- Consumes: `FileLogger`, `LogFile`, `Timber`.
- Produces:
  - `class FileLoggingTree(private val logger: FileLogger) : Timber.DebugTree()` overriding `log(priority: Int, tag: String?, message: String, t: Throwable?)`. Routing: tag `"PARSE"` → `PARSE_FAILURES`; tag `"UNPARSED"` → `UNPARSED_SMS`; priority `Log.ERROR` or `Log.WARN` → `ERROR_LOG`; else no file write.
  - `@Singleton class LoggingSetup @Inject constructor(private val fileLogger: FileLogger)` with `fun install()`: plants `DebugTree` + `FileLoggingTree` (only if `Timber.treeCount() == 0`); installs `Thread.setDefaultUncaughtExceptionHandler` that writes `[thread name + stacktrace]` to `CRASH_LOG` then delegates to the previous handler.
  - `@Module` `LoggingModule` providing `@Singleton FileLogger(@ApplicationContext context: Context)` = `FileLogger(context, context.filesDir)`.

- [ ] **Step 1: Write the failing test**

Create `FileLoggingTreeTest.kt`:

```kotlin
package com.smsexpensetracker.data.logging

import android.content.Context
import android.util.Log
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FileLoggingTreeTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var logger: FileLogger
    private lateinit var tree: FileLoggingTree

    @Before
    fun setup() {
        logger = FileLogger(mockk<Context>(), tempDir.root)
        tree = FileLoggingTree(logger)
        Timber.plant(tree)
    }

    @After
    fun teardown() {
        Timber.uprootAll()
    }

    @Test
    fun `error priority writes to error_log`() = runTest {
        Timber.e("boom")
        assertTrue(File(tempDir.root, "logs/error_log.txt").readText().contains("boom"))
    }

    @Test
    fun `warn priority writes to error_log`() = runTest {
        Timber.w("careful")
        assertTrue(File(tempDir.root, "logs/error_log.txt").readText().contains("careful"))
    }

    @Test
    fun `parse tag writes to parse_failures`() = runTest {
        Timber.tag("PARSE").w("parse failed")
        assertTrue(File(tempDir.root, "logs/parse_failures.txt").readText().contains("parse failed"))
    }

    @Test
    fun `unparsed tag writes to unparsed_sms`() = runTest {
        Timber.tag("UNPARSED").w("no match")
        assertTrue(File(tempDir.root, "logs/unparsed_sms.txt").readText().contains("no match"))
    }

    @Test
    fun `debug priority writes no file`() = runTest {
        Timber.d("noise")
        assertFalse(File(tempDir.root, "logs/error_log.txt").exists())
    }

    @Test
    fun `parse tag at debug priority still routes to parse_failures`() = runTest {
        Timber.tag("PARSE").d("low priority parse note")
        assertTrue(File(tempDir.root, "logs/parse_failures.txt").readText().contains("low priority parse note"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.logging.FileLoggingTreeTest"`
Expected: FAIL (unresolved `FileLoggingTree`).

- [ ] **Step 3: Write the implementation**

Create `data/logging/FileLoggingTree.kt`:

```kotlin
package com.smsexpensetracker.data.logging

import android.util.Log
import timber.log.Timber

class FileLoggingTree(private val logger: FileLogger) : Timber.DebugTree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val target = when {
            tag == "PARSE" -> LogFile.PARSE_FAILURES
            tag == "UNPARSED" -> LogFile.UNPARSED_SMS
            priority == Log.ERROR || priority == Log.WARN -> LogFile.ERROR_LOG
            else -> return
        }
        logger.appendBlocking(target, message)
    }
}
```

Create `data/logging/LoggingSetup.kt`:

```kotlin
package com.smsexpensetracker.data.logging

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoggingSetup @Inject constructor(
    private val fileLogger: FileLogger
) {

    fun install() {
        if (Timber.treeCount() == 0) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(FileLoggingTree(fileLogger))
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            fileLogger.appendBlocking(
                LogFile.CRASH_LOG,
                "Thread: ${thread.name}\n${throwable.stackTraceToString()}"
            )
            previous?.uncaughtException(thread, throwable)
        }
    }
}
```

Create `di/LoggingModule.kt`:

```kotlin
package com.smsexpensetracker.di

import android.content.Context
import com.smsexpensetracker.data.logging.FileLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
    @Provides
    @Singleton
    fun provideFileLogger(@ApplicationContext context: Context): FileLogger =
        FileLogger(context, context.filesDir)
}
```

Modify `SmsExpenseApp.kt` — add the field and the `install()` call:

```kotlin
    @Inject
    lateinit var demoDataSeeder: DemoDataSeeder

    @Inject
    lateinit var loggingSetup: com.smsexpensetracker.data.logging.LoggingSetup

    override fun onCreate() {
        super.onCreate()
        loggingSetup.install()
        appScope.launch { demoDataSeeder.seedIfEmpty() }
    }
```

(Add the import `com.smsexpensetracker.data.logging.LoggingSetup` and remove the fully-qualified reference if preferred.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.logging.FileLoggingTreeTest"`
Expected: PASS (all 6 tests).

- [ ] **Step 5: Run the full test suite + build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: green. (Hilt now resolves `FileLogger` + `LoggingSetup`; app compiles with the new `@Inject` field.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/data/logging/FileLoggingTree.kt app/src/main/java/com/smsexpensetracker/data/logging/LoggingSetup.kt app/src/main/java/com/smsexpensetracker/di/LoggingModule.kt app/src/main/java/com/smsexpensetracker/SmsExpenseApp.kt app/src/test/java/com/smsexpensetracker/data/logging/FileLoggingTreeTest.kt
git commit -m "feat(data): add FileLoggingTree, LoggingSetup crash handler, and app wiring"
```

---

### Task 4: CsvExporter + FileProvider manifest/resources

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/data/csv/CsvExporter.kt`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/smsexpensetracker/data/csv/CsvExporterTest.kt`

**Interfaces:**
- Consumes: `android.content.Context`, `TransactionRepository.getAllTransactions()`, `CsvCodec.toCsv`, `FILE_PROVIDER_AUTHORITY`, `FileProvider`.
- Produces:
  - `data class ExportResult(val uri: Uri, val fileName: String, val count: Int)`
  - `data class ExportFile(val file: File, val count: Int)`
  - `@Singleton class CsvExporter(context: Context, baseDir: File, transactionRepository: TransactionRepository)` with `suspend fun exportAll(): ExportResult` and `internal suspend fun buildExportFile(): ExportFile` (writes `baseDir/exports/transactions_yyyyMMdd_HHmmss.csv`).

- [ ] **Step 1: Write the failing test**

Create `CsvExporterTest.kt`:

```kotlin
package com.smsexpensetracker.data.csv

import android.content.Context
import com.smsexpensetracker.core.csv.CsvCodec
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class CsvExporterTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val repository = mockk<TransactionRepository>()

    private val tx = Transaction(
        id = 1L, bankId = 2L, amount = 100L, transactionType = TransactionType.CREDIT,
        description = "Refund", transactionDate = LocalDateTime.of(2026, 8, 2, 9, 0),
        categoryId = null, rawSms = "SMS", smsTimestamp = 1L,
        createdAt = LocalDateTime.of(2026, 8, 2, 9, 0), parseMethod = ParseMethod.SMS
    )

    @Test
    fun `buildExportFile writes file under exports dir with content`() = runTest {
        coEvery { repository.getAllTransactions() } returns flowOf(listOf(tx))
        val exporter = CsvExporter(mockk<Context>(), tempDir.root, repository)

        val result = exporter.buildExportFile()

        assertTrue(result.file.parentFile?.name == "exports")
        assertTrue(result.file.name.startsWith("transactions_"))
        assertEquals(1, result.count)
        val rows = CsvCodec.parse(result.file.readText())
        assertEquals(CsvCodec.HEADER, rows.first())
        assertEquals("100", rows[1][1])
        assertEquals("CREDIT", rows[1][2])
    }

    @Test
    fun `buildExportFile writes header-only csv for empty data`() = runTest {
        coEvery { repository.getAllTransactions() } returns flowOf(emptyList())
        val exporter = CsvExporter(mockk<Context>(), tempDir.root, repository)

        val result = exporter.buildExportFile()

        assertEquals(0, result.count)
        val rows = CsvCodec.parse(result.file.readText())
        assertEquals(1, rows.size)
        assertEquals(CsvCodec.HEADER, rows.first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.csv.CsvExporterTest"`
Expected: FAIL (unresolved `CsvExporter`).

- [ ] **Step 3: Write the implementation**

Create `data/csv/CsvExporter.kt`:

```kotlin
package com.smsexpensetracker.data.csv

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.smsexpensetracker.core.csv.CsvCodec
import com.smsexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ExportResult(val uri: Uri, val fileName: String, val count: Int)

data class ExportFile(val file: File, val count: Int)

@Singleton
class CsvExporter @Inject constructor(
    private val context: Context,
    private val baseDir: File,
    private val transactionRepository: TransactionRepository
) {

    suspend fun exportAll(): ExportResult {
        val exportFile = buildExportFile()
        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, exportFile.file)
        return ExportResult(uri, exportFile.file.name, exportFile.count)
    }

    internal suspend fun buildExportFile(): ExportFile {
        val transactions = transactionRepository.getAllTransactions().first()
        val csv = CsvCodec.toCsv(transactions)
        val dir = File(baseDir, "exports").apply { mkdirs() }
        val fileName = "transactions_" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv"
        val file = File(dir, fileName)
        withContext(Dispatchers.IO) { file.writeText(csv, Charsets.UTF_8) }
        return ExportFile(file, transactions.size)
    }
}
```

Create `res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="exports" path="exports/" />
    <files-path name="logs" path="logs/" />
</paths>
```

Modify `AndroidManifest.xml` — inside `<application>` (after the `<activity>` block):

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="com.smsexpensetracker.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.csv.CsvExporterTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Run the full test suite + build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/data/csv/CsvExporter.kt app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml app/src/test/java/com/smsexpensetracker/data/csv/CsvExporterTest.kt
git commit -m "feat(data): add CsvExporter and FileProvider manifest wiring"
```

---

### Task 5: CsvImporter

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/data/csv/CsvImporter.kt`
- Test: `app/src/test/java/com/smsexpensetracker/data/csv/CsvImporterTest.kt`

**Interfaces:**
- Consumes: `android.content.ContentResolver`, `TransactionRepository.getAllTransactions()` + `insertBatch`, `CsvCodec.parse`/`requireHeader`/`toTransactionRow`, `Transaction`.
- Produces:
  - `data class ImportResult(val imported: Int, val skipped: Int, val invalid: Int)`
  - `@Singleton class CsvImporter(contentResolver: ContentResolver, transactionRepository: TransactionRepository)` with `suspend fun importFrom(uri: Uri): ImportResult` and `internal suspend fun importFromText(text: String): ImportResult`.

- [ ] **Step 1: Write the failing test**

Create `CsvImporterTest.kt`:

```kotlin
package com.smsexpensetracker.data.csv

import android.content.ContentResolver
import com.smsexpensetracker.core.csv.CsvCodec
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class CsvImporterTest {

    private val repository = mockk<TransactionRepository>()
    private val resolver = mockk<ContentResolver>()

    private val existing = Transaction(
        id = 1L, bankId = 2L, amount = 500L, transactionType = TransactionType.CREDIT,
        description = "Refund", transactionDate = LocalDateTime.of(2026, 8, 1, 9, 0),
        categoryId = null, rawSms = "SMS", smsTimestamp = 1L,
        createdAt = LocalDateTime.of(2026, 8, 1, 9, 0), parseMethod = ParseMethod.SMS
    )

    private val importer = CsvImporter(resolver, repository)

    private fun csvWith(vararg rows: List<String>): String =
        CsvCodec.encode(listOf(CsvCodec.HEADER) + rows.toList())

    @Test
    fun `importFromText inserts valid rows`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        coEvery { repository.insertBatch(any()) } returns 2
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "A", "2", "", "0", "SMS", "raw1"),
            listOf("2026-08-02T11:00:00", "200", "CREDIT", "B", "3", "", "0", "SMS", "raw2")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 2, skipped = 0, invalid = 0), result)
        coVerifyInserted(2)
    }

    @Test
    fun `importFromText counts invalid rows but inserts valid ones`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        coEvery { repository.insertBatch(any()) } returns 1
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "abc", "DEBIT", "bad amount", "2", "", "0", "SMS", "raw"),
            listOf("2026-08-02T10:00:00", "0", "DEBIT", "zero", "2", "", "0", "SMS", "raw"),
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "good", "2", "", "0", "SMS", "raw")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 1, skipped = 0, invalid = 2), result)
    }

    @Test
    fun `importFromText skips rows matching existing transactions`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(listOf(existing))
        coEvery { repository.insertBatch(any()) } returns 1
        val text = csvWith(
            listOf("2026-08-01T09:00:00", "500", "CREDIT", "Refund", "2", "", "0", "SMS", "SMS"),
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "New", "2", "", "0", "SMS", "raw")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 1, skipped = 1, invalid = 0), result)
        coVerifyInserted(1)
    }

    @Test
    fun `importFromText dedups duplicate rows within file`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        coEvery { repository.insertBatch(any()) } returns 1
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "A", "2", "", "0", "SMS", "raw"),
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "A", "2", "", "0", "SMS", "raw")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 1, skipped = 1, invalid = 0), result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `importFromText throws on malformed csv structure`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        importer.importFromText("\"unterminated")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `importFromText throws on wrong header`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        importer.importFromText("foo,bar\n1,2")
    }

    @Test
    fun `importFrom returns empty result for missing bankId rows`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "no bank", "", "", "0", "SMS", "raw")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 0, skipped = 0, invalid = 1), result)
    }

    private suspend fun coVerifyInserted(count: Int) {
        io.mockk.coVerify(exactly = 1) {
            repository.insertBatch(match { list -> list.size == count && list.all { it.bankId > 0 } })
        }
    }
}
```

Note: the `coVerifyInserted` helper uses the fully-qualified `io.mockk.coVerify` reference — no extra import needed.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.csv.CsvImporterTest"`
Expected: FAIL (unresolved `CsvImporter`).

- [ ] **Step 3: Write the implementation**

Create `data/csv/CsvImporter.kt`:

```kotlin
package com.smsexpensetracker.data.csv

import android.content.ContentResolver
import android.net.Uri
import com.smsexpensetracker.core.csv.CsvCodec
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val imported: Int, val skipped: Int, val invalid: Int)

@Singleton
class CsvImporter @Inject constructor(
    private val contentResolver: ContentResolver,
    private val transactionRepository: TransactionRepository
) {

    suspend fun importFrom(uri: Uri): ImportResult {
        val text = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        }
        return importFromText(text)
    }

    internal suspend fun importFromText(text: String): ImportResult {
        val rows = CsvCodec.parse(text)
        CsvCodec.requireHeader(rows)

        val seen = transactionRepository.getAllTransactions().first()
            .map { Triple(it.amount, it.transactionDate, it.description) }
            .toMutableSet()

        var skipped = 0
        var invalid = 0
        val candidates = mutableListOf<Transaction>()

        for (raw in rows.drop(1)) {
            val row = CsvCodec.toTransactionRow(raw)
            if (row == null || row.amount <= 0L || row.bankId == null) {
                invalid++
                continue
            }
            val key = Triple(row.amount, row.transactionDate, row.description)
            if (!seen.add(key)) {
                skipped++
                continue
            }
            candidates += Transaction(
                id = 0L,
                bankId = row.bankId,
                amount = row.amount,
                transactionType = row.type,
                description = row.description,
                transactionDate = row.transactionDate,
                categoryId = row.categoryId,
                rawSms = row.rawSms,
                smsTimestamp = row.smsTimestamp,
                createdAt = LocalDateTime.now(),
                parseMethod = row.parseMethod
            )
        }

        val imported = if (candidates.isEmpty()) 0 else transactionRepository.insertBatch(candidates)
        return ImportResult(imported = imported, skipped = skipped, invalid = invalid)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.csv.CsvImporterTest"`
Expected: PASS (all 7 tests).

- [ ] **Step 5: Run the full test suite + build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/data/csv/CsvImporter.kt app/src/test/java/com/smsexpensetracker/data/csv/CsvImporterTest.kt
git commit -m "feat(data): add CsvImporter with validation and dedup"
```

---

### Task 6: Use cases + CsvModule

**Files:**
- Rewrite: `app/src/main/java/com/smsexpensetracker/domain/usecase/ExportCsvUseCase.kt`
- Create: `app/src/main/java/com/smsexpensetracker/domain/usecase/ImportCsvUseCase.kt`
- Create: `app/src/main/java/com/smsexpensetracker/di/CsvModule.kt`
- Test: `app/src/test/java/com/smsexpensetracker/domain/usecase/ExportCsvUseCaseTest.kt`
- Test: `app/src/test/java/com/smsexpensetracker/domain/usecase/ImportCsvUseCaseTest.kt`

**Interfaces:**
- Consumes: `CsvExporter.exportAll()`, `CsvImporter.importFrom(uri)`, `ExportResult`, `ImportResult`.
- Produces:
  - `@Singleton class ExportCsvUseCase @Inject constructor(private val exporter: CsvExporter)` with `suspend operator fun invoke(): Result<ExportResult>`.
  - `@Singleton class ImportCsvUseCase @Inject constructor(private val importer: CsvImporter)` with `suspend operator fun invoke(uri: Uri): Result<ImportResult>`.
  - Both rethrow `CancellationException`, map other exceptions to `Result.failure`.
  - `@Module` `CsvModule`: provides `@Singleton CsvExporter(@ApplicationContext context, TransactionRepository)` = `CsvExporter(context, File(context.filesDir), transactionRepository)`; provides `@Singleton CsvImporter(@ApplicationContext context, TransactionRepository)` = `CsvImporter(context.contentResolver, transactionRepository)`.
  - Deletes the old no-arg `ExportCsvUseCase` stub (no other callers exist — verified).

- [ ] **Step 1: Write the failing test**

Create `ExportCsvUseCaseTest.kt`:

```kotlin
package com.smsexpensetracker.domain.usecase

import android.net.Uri
import com.smsexpensetracker.data.csv.CsvExporter
import com.smsexpensetracker.data.csv.ExportResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportCsvUseCaseTest {

    private val exporter = mockk<CsvExporter>()
    private val useCase = ExportCsvUseCase(exporter)

    @Test
    fun `invoke returns success with export result`() = runTest {
        val result = ExportResult(mockk<Uri>(), "transactions_x.csv", 3)
        coEvery { exporter.exportAll() } returns result

        val outcome = useCase()

        assertEquals(result, outcome.getOrThrow())
    }

    @Test
    fun `invoke returns failure when exporter throws`() = runTest {
        coEvery { exporter.exportAll() } throws RuntimeException("disk full")

        val outcome = useCase()

        assertTrue(outcome.isFailure)
    }

    @Test(expected = CancellationException::class)
    fun `invoke rethrows cancellation`() = runTest {
        coEvery { exporter.exportAll() } throws CancellationException("cancel")

        useCase()

        coVerify(exactly = 1) { exporter.exportAll() }
    }
}
```

Create `ImportCsvUseCaseTest.kt`:

```kotlin
package com.smsexpensetracker.domain.usecase

import android.net.Uri
import com.smsexpensetracker.data.csv.CsvImporter
import com.smsexpensetracker.data.csv.ImportResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportCsvUseCaseTest {

    private val importer = mockk<CsvImporter>()
    private val useCase = ImportCsvUseCase(importer)
    private val uri = mockk<Uri>()

    @Test
    fun `invoke returns success with import result`() = runTest {
        val result = ImportResult(imported = 5, skipped = 1, invalid = 2)
        coEvery { importer.importFrom(uri) } returns result

        val outcome = useCase(uri)

        assertEquals(result, outcome.getOrThrow())
    }

    @Test
    fun `invoke returns failure when importer throws`() = runTest {
        coEvery { importer.importFrom(uri) } throws IllegalArgumentException("bad header")

        val outcome = useCase(uri)

        assertTrue(outcome.isFailure)
    }

    @Test(expected = CancellationException::class)
    fun `invoke rethrows cancellation`() = runTest {
        coEvery { importer.importFrom(uri) } throws CancellationException("cancel")

        useCase(uri)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.ExportCsvUseCaseTest" --tests "com.smsexpensetracker.domain.usecase.ImportCsvUseCaseTest"`
Expected: FAIL (compile error: `ExportCsvUseCase` constructor mismatch, unresolved `ImportCsvUseCase`).

- [ ] **Step 3: Write the implementation**

Rewrite `domain/usecase/ExportCsvUseCase.kt`:

```kotlin
package com.smsexpensetracker.domain.usecase

import com.smsexpensetracker.data.csv.CsvExporter
import com.smsexpensetracker.data.csv.ExportResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportCsvUseCase @Inject constructor(
    private val exporter: CsvExporter
) {
    suspend operator fun invoke(): Result<ExportResult> = try {
        Result.success(exporter.exportAll())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

Create `domain/usecase/ImportCsvUseCase.kt`:

```kotlin
package com.smsexpensetracker.domain.usecase

import android.net.Uri
import com.smsexpensetracker.data.csv.CsvImporter
import com.smsexpensetracker.data.csv.ImportResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportCsvUseCase @Inject constructor(
    private val importer: CsvImporter
) {
    suspend operator fun invoke(uri: Uri): Result<ImportResult> = try {
        Result.success(importer.importFrom(uri))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

Create `di/CsvModule.kt`:

```kotlin
package com.smsexpensetracker.di

import android.content.Context
import com.smsexpensetracker.data.csv.CsvExporter
import com.smsexpensetracker.data.csv.CsvImporter
import com.smsexpensetracker.domain.repository.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CsvModule {
    @Provides
    @Singleton
    fun provideCsvExporter(
        @ApplicationContext context: Context,
        transactionRepository: TransactionRepository
    ): CsvExporter = CsvExporter(context, File(context.filesDir), transactionRepository)

    @Provides
    @Singleton
    fun provideCsvImporter(
        @ApplicationContext context: Context,
        transactionRepository: TransactionRepository
    ): CsvImporter = CsvImporter(context.contentResolver, transactionRepository)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.ExportCsvUseCaseTest" --tests "com.smsexpensetracker.domain.usecase.ImportCsvUseCaseTest"`
Expected: PASS (all 6 tests).

- [ ] **Step 5: Run the full test suite + build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: green (Hilt resolves the new use cases + module).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/usecase/ExportCsvUseCase.kt app/src/main/java/com/smsexpensetracker/domain/usecase/ImportCsvUseCase.kt app/src/main/java/com/smsexpensetracker/di/CsvModule.kt app/src/test/java/com/smsexpensetracker/domain/usecase/ExportCsvUseCaseTest.kt app/src/test/java/com/smsexpensetracker/domain/usecase/ImportCsvUseCaseTest.kt
git commit -m "feat(domain): rewrite ExportCsvUseCase and add ImportCsvUseCase with DI module"
```

---

### Task 7: LogViewerViewModel + Screen + NavGraph

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/logs/LogViewerViewModel.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/logs/LogViewerScreen.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/logs/LogViewerViewModelTest.kt`

**Interfaces:**
- Consumes: `ParseLogRepository.getAllLogs()`, `FileLogger.readAll()`/`clear(file)`/`logFileUri(file)`, `LogFile`, `ParseLog`.
- Produces:
  - `@HiltViewModel class LogViewerViewModel(parseLogRepository, fileLogger)` with `val parseLogs: StateFlow<List<ParseLog>>` (`stateIn(viewModelScope, WhileSubscribed(5000), emptyList())`), `val fileLogs: StateFlow<Map<LogFile, String>>`, `fun refresh()`, `fun clearFile(file: LogFile)`.
  - `@Composable fun LogViewerScreen(onBack: () -> Unit, viewModel: LogViewerViewModel = hiltViewModel())`.
  - NavGraph route `"logs"`.

- [ ] **Step 1: Write the failing test**

Create `LogViewerViewModelTest.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.logs

import android.content.Context
import com.smsexpensetracker.data.logging.FileLogger
import com.smsexpensetracker.data.logging.LogFile
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.repository.ParseLogRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val tempDir = TemporaryFolder()

    private val parseLogRepository = mockk<ParseLogRepository>()
    private lateinit var fileLogger: FileLogger

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fileLogger = FileLogger(mockk<Context>(), tempDir.root)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exposes parse logs flow`() = runTest(testDispatcher) {
        val log = ParseLog(1L, "body", "HDFCBK", LocalDateTime.of(2026, 8, 2, 9, 0), ParseStatus.FAILED, "err")
        every { parseLogRepository.getAllLogs() } returns flowOf(listOf(log))
        val viewModel = LogViewerViewModel(parseLogRepository, fileLogger)
        val job = launch { viewModel.parseLogs.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(log), viewModel.parseLogs.value)
        job.cancel()
    }

    @Test
    fun `refresh populates fileLogs`() = runTest(testDispatcher) {
        fileLogger.append(LogFile.ERROR_LOG, "line one")
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        val viewModel = LogViewerViewModel(parseLogRepository, fileLogger)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(4, viewModel.fileLogs.value.size)
        assertEquals(true, viewModel.fileLogs.value.getValue(LogFile.ERROR_LOG).contains("line one"))
    }

    @Test
    fun `clearFile empties the file and refreshes`() = runTest(testDispatcher) {
        fileLogger.append(LogFile.ERROR_LOG, "to be cleared")
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        val viewModel = LogViewerViewModel(parseLogRepository, fileLogger)
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.clearFile(LogFile.ERROR_LOG)
        advanceUntilIdle()

        assertEquals("", viewModel.fileLogs.value.getValue(LogFile.ERROR_LOG))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.logs.LogViewerViewModelTest"`
Expected: FAIL (unresolved `LogViewerViewModel`).

- [ ] **Step 3: Write the implementation**

Create `ui/screens/logs/LogViewerViewModel.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.data.logging.FileLogger
import com.smsexpensetracker.data.logging.LogFile
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.repository.ParseLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    parseLogRepository: ParseLogRepository,
    private val fileLogger: FileLogger
) : ViewModel() {

    val parseLogs: StateFlow<List<ParseLog>> = parseLogRepository.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _fileLogs = MutableStateFlow<Map<LogFile, String>>(emptyMap())
    val fileLogs: StateFlow<Map<LogFile, String>> = _fileLogs.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _fileLogs.value = fileLogger.readAll() }
    }

    fun clearFile(file: LogFile) {
        viewModelScope.launch {
            fileLogger.clear(file)
            _fileLogs.value = fileLogger.readAll()
        }
    }
}
```

Create `ui/screens/logs/LogViewerScreen.kt` (no unit tests — mirrors `ParserScreen`/`RuleEditorScreen` visual conventions):

```kotlin
package com.smsexpensetracker.ui.screens.logs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.data.logging.LogFile
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseStatus
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
    viewModel: LogViewerViewModel = hiltViewModel()
) {
    val parseLogs by viewModel.parseLogs.collectAsState()
    val fileLogs by viewModel.fileLogs.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "File Logs",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LogFile.entries.forEach { file ->
                item(key = "file_${file.name}") {
                    FileLogCard(
                        file = file,
                        content = fileLogs[file].orEmpty(),
                        onShare = {
                            try {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, viewModel.logFileUri(file))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
                            } catch (e: ActivityNotFoundException) {
                                snackbarHostState.showSnackbar("No app can share this file")
                            }
                        },
                        onClear = {
                            viewModel.clearFile(file)
                        }
                    )
                }
            }
            item {
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = "Parse Log",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(parseLogs, key = { it.id }) { log ->
                ParseLogRow(log)
            }
        }
    }
}

@Composable
private fun FileLogCard(
    file: LogFile,
    content: String,
    onShare: () -> Unit,
    onClear: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onShare) { Text("Share") }
                TextButton(onClick = { showClearDialog = true }) { Text("Clear") }
            }
            Text(
                text = content.takeLast(2000).ifBlank { "(empty)" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear ${file.name}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    showClearDialog = false
                    onClear()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ParseLogRow(log: ParseLog) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.smsSender,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = log.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (log.status) {
                        ParseStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        ParseStatus.FAILED -> MaterialTheme.colorScheme.error
                        ParseStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                text = log.parsedAt.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            log.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
```

The screen calls `viewModel.logFileUri(file)` — add this method to `LogViewerViewModel`:

```kotlin
    fun logFileUri(file: LogFile): android.net.Uri = fileLogger.logFileUri(file)
```

Modify `NavGraph.kt` — add the import `com.smsexpensetracker.ui.screens.logs.LogViewerScreen`, add `onNavigateToLogs` to the Settings route, and add a `"logs"` composable:

```kotlin
        composable(BottomNavItem.Settings.route) {
            SettingsScreen(
                onNavigateToCategories = { navController.navigate("categories") },
                onNavigateToBanks = { navController.navigate("banks") },
                onNavigateToLogs = { navController.navigate("logs") }
            )
        }
        composable("logs") {
            LogViewerScreen(onBack = { navController.popBackStack() })
        }
```

Note: `SettingsScreen` does not accept `onNavigateToLogs` yet — add it in Task 8. If the build fails at the end of this task because of the missing parameter, add `onNavigateToLogs: () -> Unit = {}` to `SettingsScreen`'s signature now (default no-op) and wire the row in Task 8.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.logs.LogViewerViewModelTest"`
Expected: PASS (all 3 tests).

- [ ] **Step 5: Run the full test suite + build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/logs app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt app/src/test/java/com/smsexpensetracker/ui/screens/logs/LogViewerViewModelTest.kt
git commit -m "feat(ui): add log viewer screen with file and parse log sections"
```

---

### Task 8: Settings wiring (Export / Import / Logs rows)

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModelTest.kt` (update constructor + new tests)

**Interfaces:**
- Consumes: `ThemePreferences`, `ExportCsvUseCase`, `ImportCsvUseCase`, `ExportResult`.
- Produces:
  - `SettingsUiState` gains `csvMessage: String? = null`, `isCsvBusy: Boolean = false`, `pendingExport: ExportResult? = null`.
  - `SettingsViewModel` gains `exportCsv()`, `importCsv(uri: Uri)`, `consumeCsvMessage()`, `consumePendingExport()`.
  - `SettingsScreen(onNavigateToCategories, onNavigateToBanks, onNavigateToLogs)` adds 3 rows + SAF launcher + share intent + snackbar.

- [ ] **Step 1: Write the failing test**

Update `SettingsViewModelTest.kt` — change the constructor calls to pass the two mocked use cases, and add new tests:

```kotlin
package com.smsexpensetracker.ui.screens.settings

import android.net.Uri
import com.smsexpensetracker.core.settings.ThemePreferences
import com.smsexpensetracker.data.csv.ExportResult
import com.smsexpensetracker.data.csv.ImportResult
import com.smsexpensetracker.domain.usecase.ExportCsvUseCase
import com.smsexpensetracker.domain.usecase.ImportCsvUseCase
import com.smsexpensetracker.ui.theme.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val themePreferences = mockk<ThemePreferences>()
    private val exportCsvUseCase = mockk<ExportCsvUseCase>()
    private val importCsvUseCase = mockk<ImportCsvUseCase>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(themePreferences, exportCsvUseCase, importCsvUseCase)

    @Test
    fun `exposes persisted theme mode`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.DARK)
        val viewModel = viewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        job.cancel()
    }

    @Test
    fun `change persists the selected mode`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { themePreferences.setThemeMode(any()) } returns Unit
        val viewModel = viewModel()
        viewModel.onThemeModeChange(ThemeMode.AMOLED)
        advanceUntilIdle()
        coVerify { themePreferences.setThemeMode(ThemeMode.AMOLED) }
    }

    @Test
    fun `exportCsv sets pendingExport on success`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        val result = ExportResult(mockk<Uri>(), "transactions_x.csv", 3)
        coEvery { exportCsvUseCase() } returns Result.success(result)
        val viewModel = viewModel()

        viewModel.exportCsv()
        advanceUntilIdle()

        assertEquals(result, viewModel.uiState.value.pendingExport)
        assertEquals("Exported 3 transactions", viewModel.uiState.value.csvMessage)
        assertFalse(viewModel.uiState.value.isCsvBusy)
    }

    @Test
    fun `exportCsv sets error message on failure`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { exportCsvUseCase() } returns Result.failure(RuntimeException("disk"))
        val viewModel = viewModel()

        viewModel.exportCsv()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingExport)
        assertTrue(viewModel.uiState.value.csvMessage?.contains("Export failed") == true)
    }

    @Test
    fun `importCsv sets success message`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { importCsvUseCase(any()) } returns Result.success(ImportResult(5, 1, 2))
        val viewModel = viewModel()

        viewModel.importCsv(mockk<Uri>())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.csvMessage?.contains("Imported 5") == true)
        assertFalse(viewModel.uiState.value.isCsvBusy)
    }

    @Test
    fun `importCsv sets error message on failure`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { importCsvUseCase(any()) } returns Result.failure(IllegalArgumentException("bad header"))
        val viewModel = viewModel()

        viewModel.importCsv(mockk<Uri>())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.csvMessage?.contains("Import failed") == true)
    }

    @Test
    fun `isCsvBusy gates concurrent export calls`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { exportCsvUseCase() } returns Result.success(ExportResult(mockk<Uri>(), "a.csv", 1))
        val viewModel = viewModel()

        viewModel.exportCsv()
        viewModel.exportCsv()
        advanceUntilIdle()

        coVerify(exactly = 1) { exportCsvUseCase() }
    }

    @Test
    fun `consumeCsvMessage clears the message`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { exportCsvUseCase() } returns Result.failure(RuntimeException("x"))
        val viewModel = viewModel()
        viewModel.exportCsv()
        advanceUntilIdle()

        viewModel.consumeCsvMessage()

        assertNull(viewModel.uiState.value.csvMessage)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.settings.SettingsViewModelTest"`
Expected: FAIL (compile error: constructor mismatch).

- [ ] **Step 3: Write the implementation**

Rewrite `SettingsViewModel.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.settings.ThemePreferences
import com.smsexpensetracker.data.csv.ExportResult
import com.smsexpensetracker.domain.usecase.ExportCsvUseCase
import com.smsexpensetracker.domain.usecase.ImportCsvUseCase
import com.smsexpensetracker.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val csvMessage: String? = null,
    val isCsvBusy: Boolean = false,
    val pendingExport: ExportResult? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val exportCsvUseCase: ExportCsvUseCase,
    private val importCsvUseCase: ImportCsvUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = combine(
        themePreferences.themeMode,
        _uiState
    ) { theme, state -> state.copy(themeMode = theme) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }

    fun exportCsv() {
        if (_uiState.value.isCsvBusy) return
        _uiState.update { it.copy(isCsvBusy = true) }
        viewModelScope.launch {
            val result = exportCsvUseCase()
            _uiState.update {
                result.fold(
                    onSuccess = { export ->
                        it.copy(
                            isCsvBusy = false,
                            pendingExport = export,
                            csvMessage = "Exported ${export.count} transactions"
                        )
                    },
                    onFailure = { e ->
                        it.copy(isCsvBusy = false, csvMessage = "Export failed: ${e.message}")
                    }
                )
            }
        }
    }

    fun importCsv(uri: Uri) {
        if (_uiState.value.isCsvBusy) return
        _uiState.update { it.copy(isCsvBusy = true) }
        viewModelScope.launch {
            val result = importCsvUseCase(uri)
            _uiState.update {
                result.fold(
                    onSuccess = { r ->
                        it.copy(
                            isCsvBusy = false,
                            csvMessage = "Imported ${r.imported}, skipped ${r.skipped}, invalid ${r.invalid}"
                        )
                    },
                    onFailure = { e ->
                        it.copy(isCsvBusy = false, csvMessage = "Import failed: ${e.message}")
                    }
                )
            }
        }
    }

    fun consumeCsvMessage() {
        _uiState.update { it.copy(csvMessage = null) }
    }

    fun consumePendingExport() {
        _uiState.update { it.copy(pendingExport = null) }
    }
}
```

Modify `SettingsScreen.kt` — add imports, the `onNavigateToLogs` parameter, snackbar host, export/import/logs rows, SAF launcher, and the pending-export share effect. Key additions:

```kotlin
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToCategories: () -> Unit = {},
    onNavigateToBanks: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importCsv(it) } }

    LaunchedEffect(state.csvMessage) {
        state.csvMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeCsvMessage()
        }
    }

    LaunchedEffect(state.pendingExport) {
        state.pendingExport?.let { export ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, export.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(Intent.createChooser(send, "Export CSV"))
            }
            viewModel.consumePendingExport()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ... existing title + Appearance rows unchanged ...
            // "Data" section rows unchanged, then add after "Banks & Rules" row:

            SettingsActionRow(
                icon = Icons.Filled.Share,
                label = "Export CSV",
                onClick = { viewModel.exportCsv() }
            )
            SettingsActionRow(
                icon = Icons.Filled.FileOpen,
                label = "Import CSV",
                onClick = {
                    importLauncher.launch(
                        arrayOf("text/csv", "text/comma-separated-values", "text/plain")
                    )
                }
            )
            SettingsActionRow(
                icon = Icons.Filled.Description,
                label = "Logs",
                onClick = onNavigateToLogs
            )
            // ... About section unchanged ...
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        )
    }
}
```

Add the shared row composable at the bottom of `SettingsScreen.kt` (refactor the existing Banks & Rules row to use it or add it alongside):

```kotlin
@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.settings.SettingsViewModelTest"`
Expected: PASS (all 9 tests).

- [ ] **Step 5: Run the full test suite + build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/settings app/src/test/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModelTest.kt
git commit -m "feat(ui): wire export, import, and logs rows into settings"
```

---

### Task 9: SmsSyncUseCase parse-failure logging + TODO + final verification

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt`
- Modify: `TODO.md`
- Test: no new tests; existing `SmsSyncUseCaseTest` must stay green (Timber is a no-op without planted trees).

**Interfaces:**
- Consumes: `Timber` (already a dependency), `ParseMethod`, existing sync flow.
- Produces: `Timber.tag("PARSE").w(...)` on the parse-failure branch so `FileLoggingTree` writes `parse_failures.txt` during real syncs.

- [ ] **Step 1: Add the Timber call**

In `SmsSyncUseCase.sync()`, inside the `if (parsed.errorMessage != null)` block (before `parseLogRepository.insert`), add:

```kotlin
                            Timber.tag("PARSE").w(
                                "Parse failed [${msg.sender}]: ${parsed.errorMessage}"
                            )
```

Add the import `import timber.log.Timber`.

- [ ] **Step 2: Run the existing sync tests**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.SmsSyncUseCaseTest"`
Expected: PASS (Timber with no planted trees is a no-op; all 9 existing tests green).

- [ ] **Step 3: Update TODO.md**

Change Task 8 block to complete:

```markdown
### [x] 8. Infrastructure: Logging & Backup
- [x] Implement `FileLogger` — write to `filesDir/logs/{error_log, parse_failures, unparsed_sms, crash_log}.txt`
- [x] Implement `FileLoggingTree` (Timber.Tree) — forwards log calls to FileLogger
- [x] Implement `CsvExporter` / `CsvImporter` — CSV export (query all transactions, format as CSV, share via FileProvider), CSV import (read CSV, validate, deduplicate, bulk insert)
- [x] Implement log viewer UI in Settings (File Logs + Parse Log sections)
- [x] **Verify:** CSV export writes valid file; CSV import round-trips correctly
```

Change Task 14 sub-items to complete:

```markdown
- [x] Implement CSV export/import buttons
- [x] Implement log viewer: view/share/clear error logs
```

- [ ] **Step 4: Run the full gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: green — all tests pass (215 + 12 CsvCodec + 8 FileLogger + 6 FileLoggingTree + 2 CsvExporter + 7 CsvImporter + 6 use cases + 3 LogViewer + 9 Settings − 0 removed ≈ 268), build succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt TODO.md
git commit -m "feat(sync): log parse failures via Timber PARSE tag; mark Task 8 complete"
```

- [ ] **Step 6: Manual smoke (device/emulator)**

- Sync with an SMS that fails parsing → `parse_failures.txt` populated (Settings → Logs → File Logs).
- Settings → Logs: both File Logs and Parse Log sections render; Share opens a share sheet; Clear empties the file.
- Settings → Export CSV → share sheet → save → file opens in a spreadsheet app with all transactions.
- Settings → Import CSV (choose the exported file) → snackbar "Imported N, skipped N".
- Import the same file twice → second import all-skipped.
- Import a malformed file → snackbar "Import failed".

---

## Post-Plan Verification (self-review)

- **Spec coverage:** §5 CsvCodec → Task 1. §6 FileLogger → Task 2. §7 tree/crash/app wiring → Task 3. §8 CsvExporter + §14 FileProvider → Task 4. §9 CsvImporter → Task 5. §10 use cases + §13 DI → Tasks 3/6. §11 log viewer + §15 LogViewerViewModel → Task 7. §12 Settings → Task 8. §7 SmsSyncUseCase + TODO → Task 9. §16 files all covered. §17 verification → Task 9 Step 6.
- **Placeholders:** none — every step has concrete code.
- **Type consistency:** `CsvTransactionRow`, `CsvCodec.HEADER`, `ExportResult`, `ImportResult`, `LogFile`, `FILE_PROVIDER_AUTHORITY`, `LoggingSetup.install()` are defined once and reused by name across tasks.
