# CSV Robustness Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the two Important findings from the logging-backup broad review: (1) move CPU-bound CSV work off the Main thread, and (2) stop imports from failing wholesale on foreign-key violations.

**Architecture:** Mirror the injectable-`CoroutineDispatcher` pattern already used by `FileLogger` and `SmsSyncUseCase`: `CsvImporter`/`CsvExporter` gain a defaulted `ioDispatcher` constructor param and wrap their CPU-bound bodies in `withContext(ioDispatcher)`. Then harden `CsvImporter.importFromText` to pre-validate `bankId`/`categoryId` against existing rows, counting unknown banks as `invalid` and nulling unknown categories (matching the entity's `SET_NULL` FK), so a single bad reference cannot crash the whole batch.

**Tech Stack:** Kotlin, Room, Hilt, kotlinx-coroutines, JUnit 4 + MockK + `runTest`.

## Global Constraints

- Package root: `com.smsexpensetracker`. Money is paisa `Long` — never `Double`/`BigDecimal`.
- No code comments unless the user asks for them.
- Build gate: `./gradlew testDebugUnitTest assembleDebug` must be green. No `lint`/`typecheck` configured.
- JUnit 4 with MockK mocks; `kotlinx-coroutines-test` `runTest { }` for suspend tests; `StandardTestDispatcher` + `Dispatchers.setMain` + `advanceUntilIdle` for ViewModel/scope tests.
- `CsvImporter`/`CsvExporter` each currently have BOTH an `@Inject` constructor AND a `@Singleton @Provides` binding in `di/CsvModule.kt` (Dagger's @Provides-wins rule). Adding constructor params requires updating BOTH the `@Inject` constructor and the `CsvModule` provider, plus every direct test construction.
- Existing test totals: 265 tests. Task 1 adds 2 → 267. Task 2 adds 3 → 270.
- The pre-existing uncommitted changes to `DashboardViewModel.kt` and `opencode.json` are NOT part of this plan — never stage or touch them.

---

### Task 1: Off-Main CSV CPU work (dispatcher injection)

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/data/csv/CsvImporter.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/csv/CsvExporter.kt`
- Modify: `app/src/test/java/com/smsexpensetracker/data/csv/CsvImporterTest.kt`
- Modify: `app/src/test/java/com/smsexpensetracker/data/csv/CsvExporterTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository.getAllTransactions(): Flow<List<Transaction>>`, `CsvCodec.parse/toCsv/toTransactionRow/requireHeader/HEADER`, `ImportResult`, `ExportResult`, `ExportFile`.
- Produces (for Task 2): `CsvImporter` constructor `(ContentResolver, TransactionRepository, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`; `CsvExporter` constructor `(Context, File, TransactionRepository, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`. Both `@Singleton @Inject`; public method signatures unchanged. `CsvModule` providers do NOT need changes (defaulted param).

**Problem:** `CsvImporter.importFromText` (CsvImporter.kt:30-70) parses the CSV, loads all existing transactions, and dedup-loops on the caller's dispatcher — the prod path is `SettingsViewModel`'s `viewModelScope.launch` (Main). `CsvExporter.buildExportFile` (CsvExporter.kt:34-43) serializes the whole table on Main; only the file write is on IO. Large tables/imports jank the UI.

- [ ] **Step 1: Write the failing test (importer)**

Add to `CsvImporterTest.kt`. The existing test's `importFromText` currently runs on the `runTest` "Test worker" thread; this test pins that CSV work moves off it. Add this test:

```kotlin
    @Test
    fun `importFromText performs work off the test thread`() = runTest {
        var onBackgroundThread = false
        every { repository.getAllTransactions() } answers {
            onBackgroundThread = Thread.currentThread().name != "Test worker"
            flowOf(emptyList())
        }
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "A", "2", "", "0", "SMS", "raw1")
        )

        importer.importFromText(text)

        assertTrue(onBackgroundThread)
    }
```

Add the import `import org.junit.Assert.assertTrue`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.csv.CsvImporterTest"`
Expected: FAIL — `assertTrue` fails because the work still runs on the "Test worker" thread.

- [ ] **Step 3: Write the failing test (exporter)**

Add to `CsvExporterTest.kt`:

```kotlin
    @Test
    fun `buildExportFile performs work off the test thread`() = runTest {
        var onBackgroundThread = false
        coEvery { repository.getAllTransactions() } coAnswers {
            onBackgroundThread = Thread.currentThread().name != "Test worker"
            flowOf(listOf(tx))
        }
        val exporter = CsvExporter(mockk<Context>(), tempDir.root, repository)

        exporter.buildExportFile()

        assertTrue(onBackgroundThread)
    }
```

Add the import `import org.junit.Assert.assertTrue` (check whether it's already imported; the existing file imports `assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull`).

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.csv.CsvExporterTest"`
Expected: FAIL — `assertTrue` fails because the work still runs on the "Test worker" thread.

- [ ] **Step 5: Implement the importer change**

Edit `app/src/main/java/com/smsexpensetracker/data/csv/CsvImporter.kt`:

1. Add import `kotlinx.coroutines.CoroutineDispatcher` (keep the existing `Dispatchers`/`withContext` imports).
2. Change the constructor:

```kotlin
@Singleton
class CsvImporter @Inject constructor(
    private val contentResolver: ContentResolver,
    private val transactionRepository: TransactionRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
```

3. Change `importFrom` to use the injected dispatcher for its read (replacing the hardcoded `Dispatchers.IO`):

```kotlin
    suspend fun importFrom(uri: Uri): ImportResult {
        val text = withContext(ioDispatcher) {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        }
        return importFromText(text)
    }
```

4. Wrap `importFromText`'s entire body in `withContext(ioDispatcher)`. The function becomes a single-expression function whose body is the existing logic wrapped:

```kotlin
    internal suspend fun importFromText(text: String): ImportResult = withContext(ioDispatcher) {
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
        ImportResult(imported = imported, skipped = skipped, invalid = invalid)
    }
```

(Change the trailing `return ImportResult(...)` to a bare expression — the lambda's last expression.)

- [ ] **Step 6: Implement the exporter change**

Edit `app/src/main/java/com/smsexpensetracker/data/csv/CsvExporter.kt`:

1. Add import `kotlinx.coroutines.CoroutineDispatcher` (keep existing `Dispatchers`/`withContext`).
2. Change the constructor:

```kotlin
@Singleton
class CsvExporter @Inject constructor(
    private val context: Context,
    private val baseDir: File,
    private val transactionRepository: TransactionRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
```

3. Wrap `buildExportFile`'s body in `withContext(ioDispatcher)` (this subsumes the existing `withContext(Dispatchers.IO)` around the file write, so that inner call becomes just `file.writeText(csv, Charsets.UTF_8)`):

```kotlin
    internal suspend fun buildExportFile(): ExportFile = withContext(ioDispatcher) {
        val transactions = transactionRepository.getAllTransactions().first()
        val csv = CsvCodec.toCsv(transactions)
        val dir = File(baseDir, "exports").apply { mkdirs() }
        val fileName = "transactions_" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv"
        val file = File(dir, fileName)
        file.writeText(csv, Charsets.UTF_8)
        ExportFile(file, transactions.size)
    }
```

Note: `exportAll()` (which calls `buildExportFile()` then `FileProvider.getUriForFile`) stays unchanged — the Uri call is quick and remains off the IO block.

- [ ] **Step 7: Run the full test suite + build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: green — all existing importer/exporter tests pass (the `CsvImporterTest` tests that call `importFromText` directly still pass; `runTest` awaits the real suspend), plus the 2 new tests: 267 tests, 0 failures. `assembleDebug` green (Hilt resolves the defaulted dispatcher params; the `CsvModule` `@Provides` calls still compile with the defaults).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/data/csv/CsvImporter.kt app/src/main/java/com/smsexpensetracker/data/csv/CsvExporter.kt app/src/test/java/com/smsexpensetracker/data/csv/CsvImporterTest.kt app/src/test/java/com/smsexpensetracker/data/csv/CsvExporterTest.kt
git commit -m "fix(data): move CSV parse/serialize off the main thread"
```

---

### Task 2: FK-safe CSV import (validate bank/category references)

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/data/csv/CsvImporter.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/di/CsvModule.kt`
- Modify: `app/src/test/java/com/smsexpensetracker/data/csv/CsvImporterTest.kt`

**Interfaces:**
- Consumes (from Task 1): `CsvImporter(resolver, repository, ioDispatcher)`; `BankRepository.getAllBanks(): Flow<List<Bank>>`; `CategoryRepository.getAllCategories(): Flow<List<Category>>`; `Bank.id: Long`; `Category.id: Long`.
- Produces: `CsvImporter` constructor becomes `(ContentResolver, TransactionRepository, BankRepository, CategoryRepository, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`. `CsvModule.provideCsvImporter` gains `bankRepository` and `categoryRepository` params. Behavior: rows with a `bankId` not present in the DB count as `invalid`; rows with a `categoryId` not present get `categoryId = null` (entity FK is `SET_NULL`); all other logic unchanged.

**Problem:** `TransactionEntity` declares FKs to `BankEntity` (CASCADE) and `CategoryEntity` (SET_NULL) (TransactionEntity.kt:13-26); Room enforces FKs by default. `CsvImporter` validates `bankId != null` but never that the referenced bank/category exists (CsvImporter.kt:44). A backup restored from another device, or after deleting a bank/category, throws `SQLiteConstraintException` from `insertBatch`, failing the WHOLE import even though most rows are valid.

- [ ] **Step 1: Update the test scaffolding**

Edit `app/src/test/java/com/smsexpensetracker/data/csv/CsvImporterTest.kt`:

1. Add imports:
```kotlin
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
```

2. Add mock fields and a reference-data stub helper; update the `importer` construction. Replace the existing `importer` line and add:

```kotlin
    private val bankRepository = mockk<BankRepository>()
    private val categoryRepository = mockk<CategoryRepository>()

    private val importer = CsvImporter(resolver, repository, bankRepository, categoryRepository)

    private fun stubReferenceData() {
        every { bankRepository.getAllBanks() } returns flowOf(
            listOf(Bank(id = 2L, name = "Bank 2", smsSender = "BANK2"),
                   Bank(id = 3L, name = "Bank 3", smsSender = "BANK3"))
        )
        every { categoryRepository.getAllCategories() } returns flowOf(
            listOf(Category(id = 7L, name = "Food", icon = "🍔", color = 0xFF000000.toInt(), isDefault = true))
        )
    }
```

3. Add a `stubReferenceData()` call at the START of each existing test's body that reaches `importFromText` (all 7 existing tests), BEFORE the `every { repository.getAllTransactions() } ...` line. Tests `importFromText throws on malformed csv`, `importFromText throws on wrong header`, and the missing-bankId test also need it — `stubReferenceData()` stubbing is harmless even when the parse throws.

- [ ] **Step 2: Run the existing importer tests**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.csv.CsvImporterTest"`
Expected: FAIL — compile error, `CsvImporter` constructor arity mismatch (the production constructor isn't updated yet). This is the RED for the constructor change.

- [ ] **Step 3: Write the new failing tests (FK behavior)**

Add to `CsvImporterTest.kt`:

```kotlin
    @Test
    fun `importFromText counts rows with unknown bankId as invalid`() = runTest {
        stubReferenceData()
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        coEvery { repository.insertBatch(any()) } returns 0
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "no bank", "99", "", "0", "SMS", "raw1")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 0, skipped = 0, invalid = 1), result)
        io.mockk.coVerify(exactly = 0) { repository.insertBatch(any()) }
    }

    @Test
    fun `importFromText nulls unknown categoryId on inserted row`() = runTest {
        stubReferenceData()
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        coEvery { repository.insertBatch(any()) } returns 1
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "food", "2", "99", "0", "SMS", "raw1")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 1, skipped = 0, invalid = 0), result)
        io.mockk.coVerify(exactly = 1) {
            repository.insertBatch(match { list -> list.size == 1 && list.all { it.categoryId == null } })
        }
    }

    @Test
    fun `importFromText keeps known categoryId on inserted row`() = runTest {
        stubReferenceData()
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        coEvery { repository.insertBatch(any()) } returns 1
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "food", "2", "7", "0", "SMS", "raw1")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 1, skipped = 0, invalid = 0), result)
        io.mockk.coVerify(exactly = 1) {
            repository.insertBatch(match { list -> list.size == 1 && list.all { it.categoryId == 7L } })
        }
    }
```

(Note the CSV column order: `date, amount, type, description, bankId, categoryId, smsTimestamp, parseMethod, rawSms` per `CsvCodec.HEADER`. Verify against `CsvCodec` when transcribing.)

- [ ] **Step 4: Implement the importer FK validation**

Edit `app/src/main/java/com/smsexpensetracker/data/csv/CsvImporter.kt`:

1. Add imports:
```kotlin
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
```

2. Change the constructor:

```kotlin
@Singleton
class CsvImporter @Inject constructor(
    private val contentResolver: ContentResolver,
    private val transactionRepository: TransactionRepository,
    private val bankRepository: BankRepository,
    private val categoryRepository: CategoryRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
```

3. Inside `importFromText`'s `withContext(ioDispatcher)` block, after the `seen` set is built, load reference ids:

```kotlin
        val bankIds = bankRepository.getAllBanks().first().map { it.id }.toSet()
        val categoryIds = categoryRepository.getAllCategories().first().map { it.id }.toSet()
```

4. In the row loop, add the bank-existence check right after the existing null/amount/bankId-null invalid check, and derive a safe category id before building the key/candidate:

```kotlin
            val row = CsvCodec.toTransactionRow(raw)
            if (row == null || row.amount <= 0L || row.bankId == null) {
                invalid++
                continue
            }
            if (row.bankId !in bankIds) {
                invalid++
                continue
            }
            val safeCategoryId = row.categoryId?.takeIf { it in categoryIds }
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
                categoryId = safeCategoryId,
                rawSms = row.rawSms,
                smsTimestamp = row.smsTimestamp,
                createdAt = LocalDateTime.now(),
                parseMethod = row.parseMethod
            )
```

Note the ordering: bank validation and category nulling happen BEFORE the dedup `seen.add`, so an invalid-FK row counts as `invalid`, not `skipped`.

- [ ] **Step 5: Update the CsvModule provider**

Edit `app/src/main/java/com/smsexpensetracker/di/CsvModule.kt`:

1. Add imports:
```kotlin
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
```

2. Change `provideCsvImporter`:

```kotlin
    @Provides
    @Singleton
    fun provideCsvImporter(
        @ApplicationContext context: Context,
        transactionRepository: TransactionRepository,
        bankRepository: BankRepository,
        categoryRepository: CategoryRepository
    ): CsvImporter = CsvImporter(
        context.contentResolver,
        transactionRepository,
        bankRepository,
        categoryRepository
    )
```

(`BankRepository` and `CategoryRepository` are already Hilt-bound via `RepositoryModule` — verified. `provideCsvExporter` is untouched.)

- [ ] **Step 6: Run the focused tests**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.csv.CsvImporterTest"`
Expected: PASS — all existing tests (with `stubReferenceData()` added) plus the 3 new tests: 10 tests, 0 failures.

- [ ] **Step 7: Run the full test suite + build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: green — 270 tests, 0 failures; `assembleDebug` green (Hilt resolves the two new repository injections into `CsvImporter`).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/data/csv/CsvImporter.kt app/src/main/java/com/smsexpensetracker/di/CsvModule.kt app/src/test/java/com/smsexpensetracker/data/csv/CsvImporterTest.kt
git commit -m "fix(data): validate bank and category references during csv import"
```

---

## Post-Plan Verification (self-review)

- **Spec coverage:** Finding 1 (CSV CPU work on Main) → Task 1 (dispatcher injection in `CsvImporter` + `CsvExporter`, thread-pinning tests). Finding 2 (wholesale FK import failure) → Task 2 (bank-existence → `invalid`; unknown category → `SET_NULL`-consistent null; `CsvModule` wiring; 3 tests). No gaps.
- **Placeholders:** none — every step has concrete code and exact file paths.
- **Type consistency:** `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` defaulted param matches `FileLogger.kt:22`/`SmsSyncUseCase.kt:37` convention; `bankIds`/`categoryIds` are `Set<Long>`; `safeCategoryId: Long?`. Task 2's `CsvImporter` constructor param order is `(resolver, transactionRepository, bankRepository, categoryRepository, ioDispatcher)` and is the SAME order used by the Task 2 test construction `CsvImporter(resolver, repository, bankRepository, categoryRepository)` and by `CsvModule.provideCsvImporter`. CSV column positions in tests match `CsvCodec.HEADER` (date, amount, type, description, bankId, categoryId, smsTimestamp, parseMethod, rawSms) — verify against `CsvCodec.kt` when transcribing.
- **Known risk:** `runTest` + real `Dispatchers.IO` (default) in the two new thread-pinning tests — the tests only assert the thread NAME differs from "Test worker", so they are deterministic (no timing dependency). If a CI/`runTest` quirk makes the thread name unpredictable, the tests may need the injected-`StandardTestDispatcher` variant instead — flag any failure to the controller rather than silently changing the assertion.
