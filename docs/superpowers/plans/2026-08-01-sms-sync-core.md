# Manual SMS Sync Core — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the app read and parse real bank SMS on demand — permission flow, dedup schema, `SmsSyncUseCase`, and a Sync trigger on the Transactions screen with a result snackbar.

**Architecture:** A suspend `SmsSyncUseCase` (domain) orchestrates a full inbox scan → `ParserEngine.parse` per message → `TransactionRepository.insertBatch` (SHA-256 `smsBodyHash` dedup via `INSERT … ON CONFLICT IGNORE`) → `ParseLog(FAILED)` for unparsed → `SyncMeta` update, exposing live `StateFlow<SyncProgress>`. A `PermissionManager` (data) drives the on-demand `READ_SMS` flow. The Transactions screen wires the trigger, permission flow, and result snackbar.

**Tech Stack:** Kotlin, Room/KSP, Hilt, Coroutines, MockK + `runTest` (JVM), Compose.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-01-sms-sync-core-design.md` (approved)
- All amounts are paisa `Long`. Never `Double`/`BigDecimal`.
- Sync strategy: **full scan + hash-dedup** on every sync (no date-incremental); `SmsSyncWorker`, live progress banner, and unparsed-list UI are **out of scope**.
- Read all SMS; unparsed ones are recorded as `ParseLog(status = FAILED)` — never silently dropped.
- Domain `Transaction` must NOT gain a `smsBodyHash` field (it is storage-level, computed in the repository).
- `CancellationException` must be rethrown, never swallowed.
- Gate: `./gradlew testDebugUnitTest assembleDebug` must pass at the end of each task.
- No Robolectric. Unit tests are pure JVM (JUnit 4 + MockK + `kotlinx-coroutines-test`).

---

## File Structure Map

- Modify: `core/database/entity/TransactionEntity.kt` — add `smsBodyHash`
- Modify: `core/database/SmsExpenseDatabase.kt` — version 3 + `MIGRATION_2_3`
- Modify: `core/database/dao/TransactionDao.kt` — `insertBatchIgnore`
- Modify: `domain/repository/TransactionRepository.kt` + `data/repository/TransactionRepositoryImpl.kt` — `insertBatch`
- Create: `domain/value/SyncProgress.kt`, `domain/value/SyncResult.kt`
- Create: `domain/usecase/SmsSyncUseCase.kt`; Delete: `domain/usecase/SyncSmsUseCase.kt`
- Create: `data/sms/PermissionManager.kt`
- Create: `di/SmsModule.kt`; Modify: `di/RepositoryModule.kt`
- Modify: `ui/screens/transactions/TransactionsViewModel.kt`, `TransactionsScreen.kt`
- Tests: `data/repository/TransactionRepositoryImplTest.kt`, `domain/usecase/SmsSyncUseCaseTest.kt`, `ui/screens/transactions/TransactionsViewModelTest.kt`, `androidTest/.../MigrationTest.kt`
- Docs: `TODO.md`

---

## Task 1: Schema v3 — `smsBodyHash` dedup

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/entity/TransactionEntity.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/SmsExpenseDatabase.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt`
- Test: `app/src/test/java/com/smsexpensetracker/data/repository/TransactionRepositoryImplTest.kt`
- Test: `app/src/androidTest/java/com/smsexpensetracker/core/database/MigrationTest.kt`

**Interfaces:**
- Consumes: existing `TransactionDao.insertAll`/`insert`, `TransactionRepository` interface.
- Produces:
  - `TransactionEntity.smsBodyHash: String? = null`
  - `TransactionDao.insertBatchIgnore(transactions: List<TransactionEntity>): LongArray` (returns `-1` for ignored rows)
  - `TransactionRepository.insertBatch(transactions: List<Transaction>): Int`
  - `SmsExpenseDatabase.MIGRATION_2_3`

- [ ] **Step 1: Add `smsBodyHash` to the entity**

In `TransactionEntity.kt`, append after `parseMethod`:

```kotlin
val smsBodyHash: String? = null
```

- [ ] **Step 2: Bump DB version and add `MIGRATION_2_3`**

In `SmsExpenseDatabase.kt`:
1. Change `version = 2` to `version = 3`.
2. Add after `MIGRATION_1_2`:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `smsBodyHash` TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_smsBodyHash` ON `transactions` (`smsBodyHash`)")
    }
}
```

3. Register it: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

- [ ] **Step 3: Add DAO method**

In `TransactionDao.kt`:

```kotlin
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertBatchIgnore(transactions: List<TransactionEntity>): LongArray
```

- [ ] **Step 4: Add `insertBatch` to the repository interface**

In `TransactionRepository.kt`:

```kotlin
suspend fun insertBatch(transactions: List<Transaction>): Int
```

- [ ] **Step 5: Implement `insertBatch` with SHA-256 hashing**

In `TransactionRepositoryImpl.kt`, add import `java.security.MessageDigest` and:

```kotlin
override suspend fun insertBatch(transactions: List<Transaction>): Int {
    val result = transactionDao.insertBatchIgnore(
        transactions.map { tx ->
            tx.toEntity().copy(
                smsBodyHash = tx.rawSms.takeIf { it.isNotBlank() }?.let { raw ->
                    MessageDigest.getInstance("SHA-256")
                        .digest(raw.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                }
            )
        }
    )
    return result.count { it > 0 }
}
```

- [ ] **Step 6: Write repository tests**

Add to `TransactionRepositoryImplTest.kt` (import `java.security.MessageDigest`):

```kotlin
private fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }

@Test
fun `insertBatch hashes non-blank bodies and counts inserted rows`() = runTest {
    val txs = listOf(
        Transaction(
            id = 0L, bankId = 1L, amount = 100L,
            transactionType = com.smsexpensetracker.domain.model.TransactionType.DEBIT,
            description = "a", transactionDate = date, categoryId = null,
            rawSms = "hello", smsTimestamp = 1L, createdAt = date,
            parseMethod = DomainParseMethod.SMS
        ),
        Transaction(
            id = 0L, bankId = 1L, amount = 200L,
            transactionType = com.smsexpensetracker.domain.model.TransactionType.CREDIT,
            description = "b", transactionDate = date, categoryId = null,
            rawSms = "", smsTimestamp = 2L, createdAt = date,
            parseMethod = DomainParseMethod.SMS
        )
    )
    coEvery { transactionDao.insertBatchIgnore(any()) } returns longArrayOf(1L, -1L)

    val count = repo.insertBatch(txs)

    assertEquals(1, count)
    coVerify {
        transactionDao.insertBatchIgnore(
            match<List<TransactionEntity>> { list ->
                list.size == 2 &&
                    list[0].smsBodyHash == sha256Hex("hello") &&
                    list[1].smsBodyHash == null
            }
        )
    }
}
```

- [ ] **Step 7: Write the 2→3 migration test**

Add to `MigrationTest.kt`:

```kotlin
@Test
fun migrate2To3_addsSmsBodyHashColumn_andUniqueIndex() {
    helper.createDatabase("migration-test-v3", 2).use { db ->
        db.execSQL("INSERT INTO banks (id, name, smsSender) VALUES (1, 'HDFC Bank', 'HDFCBK')")
        db.execSQL(
            "INSERT INTO transactions (bankId, amount, type, description, transactionDate, categoryId, rawSms, smsTimestamp, createdAt, parseMethod) " +
                "VALUES (1, 1000, 'DEBIT', 'desc', 1750000000, NULL, 'raw', 1750000000, 1750000000, 'MANUAL')"
        )
    }

    val db = helper.runMigrationsAndValidate("migration-test-v3", 3, true, SmsExpenseDatabase.MIGRATION_2_3)

    db.query("SELECT COUNT(*) FROM transactions").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(1L, cursor.getLong(0))
    }
    db.query("PRAGMA index_list('transactions')").use { cursor ->
        var found = false
        while (cursor.moveToNext()) {
            if (cursor.getString(1) == "index_transactions_smsBodyHash") found = true
        }
        assertTrue(found)
    }
    db.close()
}
```

- [ ] **Step 8: Run unit tests and build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL (androidTest migration test is device-only — verify on emulator later with `./gradlew connectedDebugAndroidTest`; schema `app/schemas/.../3.json` should be generated).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/database app/src/main/java/com/smsexpensetracker/domain/repository app/src/main/java/com/smsexpensetracker/data/repository app/src/test app/src/androidTest app/schemas
git commit -m "feat: add smsBodyHash dedup column, migration v3, and insertBatch repository method"
```

---

## Task 2: `SmsSyncUseCase` + value objects

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/domain/value/SyncProgress.kt`
- Create: `app/src/main/java/com/smsexpensetracker/domain/value/SyncResult.kt`
- Create: `app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt`
- Delete: `app/src/main/java/com/smsexpensetracker/domain/usecase/SyncSmsUseCase.kt`
- Test: `app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository.insertBatch(List<Transaction>): Int` (Task 1); existing `SmsReader.readSms(): Flow<List<SmsMessage>>`, `SmsRuleRepository.getAllRules()`, `ParseLogRepository.insert(ParseLog)`, `SyncMetaRepository.upsert(SyncMeta)`.
- Produces:
  - `data class SyncProgress(processed: Int = 0, total: Int = 0, unparsed: Int = 0)`
  - `data class SyncResult(scanned: Int = 0, inserted: Int = 0, unparsed: Int = 0, error: String? = null)`
  - `class SmsSyncUseCase` with `val progress: StateFlow<SyncProgress>` and `suspend fun sync(): SyncResult`, constructor `(smsReader, smsRuleRepository, transactionRepository, parseLogRepository, syncMetaRepository, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`.
  - Deletes `SyncSmsUseCase` stub (referenced nowhere).

- [ ] **Step 1: Write value objects**

`SyncProgress.kt`:

```kotlin
package com.smsexpensetracker.domain.value

data class SyncProgress(
    val processed: Int = 0,
    val total: Int = 0,
    val unparsed: Int = 0
)
```

`SyncResult.kt`:

```kotlin
package com.smsexpensetracker.domain.value

data class SyncResult(
    val scanned: Int = 0,
    val inserted: Int = 0,
    val unparsed: Int = 0,
    val error: String? = null
)
```

- [ ] **Step 2: Write the failing use-case tests**

`SmsSyncUseCaseTest.kt`:

```kotlin
package com.smsexpensetracker.domain.usecase

import com.smsexpensetracker.data.sms.SmsMessage
import com.smsexpensetracker.data.sms.SmsReader
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.repository.ParseLogRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import com.smsexpensetracker.domain.repository.SyncMetaRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import com.smsexpensetracker.domain.value.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmsSyncUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var smsReader: SmsReader
    private lateinit var smsRuleRepository: SmsRuleRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var parseLogRepository: ParseLogRepository
    private lateinit var syncMetaRepository: SyncMetaRepository
    private lateinit var useCase: SmsSyncUseCase

    private val hdfcRule = SmsRule(
        id = 1L,
        bankId = 1L,
        pattern = "Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
        description = "HDFC CC Debit"
    )

    private val hdfcSms = SmsMessage(
        id = 10L,
        sender = "AD-HDFCBK-S",
        body = "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161",
        timestamp = 1750000000000L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        smsReader = mockk()
        smsRuleRepository = mockk()
        transactionRepository = mockk()
        parseLogRepository = mockk()
        syncMetaRepository = mockk()
        useCase = SmsSyncUseCase(
            smsReader,
            smsRuleRepository,
            transactionRepository,
            parseLogRepository,
            syncMetaRepository,
            testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sync parses HDFC sms and inserts a transaction`() = runTest {
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatch(any()) } returns 1
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertEquals(SyncResult(scanned = 1, inserted = 1, unparsed = 0), result)
        coVerify {
            transactionRepository.insertBatch(
                match { list ->
                    list.size == 1 &&
                        list[0].amount == 483176L &&
                        list[0].bankId == 1L &&
                        list[0].rawSms == hdfcSms.body
                }
            )
        }
        coVerify(exactly = 0) { parseLogRepository.insert(any()) }
    }

    @Test
    fun `sync records parse log for unparsed sms`() = runTest {
        val junk = SmsMessage(11L, "UNKNOWN", "This is not a bank SMS", 1750000000001L)
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(junk))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(emptyList())
        coEvery { transactionRepository.insertBatch(any()) } returns 0
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertEquals(SyncResult(scanned = 1, inserted = 0, unparsed = 1), result)
        coVerify {
            parseLogRepository.insert(
                match { log -> log.status == ParseStatus.FAILED && log.smsBody == junk.body }
            )
        }
        coVerify(exactly = 0) { transactionRepository.insertBatch(any()) }
    }

    @Test
    fun `sync emits progress for processed messages`() = runTest {
        val messages = listOf(
            hdfcSms,
            SmsMessage(11L, "UNKNOWN", "This is not a bank SMS", 1750000000001L),
            hdfcSms.copy(id = 12L)
        )
        coEvery { smsReader.readSms() } returns MutableStateFlow(messages)
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatch(any()) } returns 1
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        useCase.sync()

        val p = useCase.progress.value
        assertEquals(3, p.processed)
        assertEquals(3, p.total)
        assertEquals(1, p.unparsed)
    }

    @Test
    fun `sync upserts sync meta on success`() = runTest {
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatch(any()) } returns 1
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        useCase.sync()

        coVerify(exactly = 1) { syncMetaRepository.upsert(any()) }
    }

    @Test
    fun `second sync while running returns empty result`() = runTest {
        val never = flow<List<SmsMessage>> { awaitCancellation() }
        coEvery { smsReader.readSms() } returns never
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))

        val first = backgroundScope.launch { useCase.sync() }
        advanceUntilIdle()
        val second = useCase.sync()

        assertEquals(SyncResult(), second)
        first.cancel()
    }

    @Test
    fun `sync returns error when insert batch fails`() = runTest {
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatch(any()) } throws RuntimeException("db down")
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertNotNull(result.error)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest`
Expected: FAIL — `SmsSyncUseCase` does not exist.

- [ ] **Step 4: Implement `SmsSyncUseCase`**

`SmsSyncUseCase.kt`:

```kotlin
package com.smsexpensetracker.domain.usecase

import com.smsexpensetracker.core.parser.ParserEngine
import com.smsexpensetracker.data.sms.SmsReader
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.model.SyncMeta
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.repository.ParseLogRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import com.smsexpensetracker.domain.repository.SyncMetaRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import com.smsexpensetracker.domain.value.SyncProgress
import com.smsexpensetracker.domain.value.SyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsSyncUseCase @Inject constructor(
    private val smsReader: SmsReader,
    private val smsRuleRepository: SmsRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val parseLogRepository: ParseLogRepository,
    private val syncMetaRepository: SyncMetaRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    private var isRunning = false

    suspend fun sync(): SyncResult {
        if (isRunning) return SyncResult()
        isRunning = true
        _progress.value = SyncProgress()
        return try {
            withContext(ioDispatcher) {
                val rules = smsRuleRepository.getAllRules().first()
                val messages = smsReader.readSms().first()
                val total = messages.size

                var processed = 0
                var unparsed = 0
                var inserted = 0

                messages.chunked(100).forEach { chunk ->
                    val transactions = mutableListOf<Transaction>()
                    for (msg in chunk) {
                        val parsed = ParserEngine.parse(
                            msg.body,
                            msg.sender,
                            rules.map { it.bankId to it.pattern }
                        )
                        if (parsed.errorMessage != null) {
                            parseLogRepository.insert(
                                ParseLog(
                                    id = 0L,
                                    smsBody = msg.body,
                                    smsSender = msg.sender,
                                    parsedAt = LocalDateTime.now(),
                                    status = ParseStatus.FAILED,
                                    errorMessage = parsed.errorMessage
                                )
                            )
                            unparsed++
                        } else if (parsed.bankId != null && parsed.amount > 0L) {
                            transactions += Transaction(
                                id = 0L,
                                bankId = parsed.bankId,
                                amount = parsed.amount,
                                transactionType = parsed.type,
                                description = parsed.description,
                                transactionDate = LocalDate.now().atStartOfDay(),
                                categoryId = null,
                                rawSms = msg.body,
                                smsTimestamp = msg.timestamp,
                                createdAt = LocalDateTime.now(),
                                parseMethod = ParseMethod.SMS
                            )
                        }
                        processed++
                    }
                    inserted += transactionRepository.insertBatch(transactions)
                    _progress.value = SyncProgress(
                        processed = processed,
                        total = total,
                        unparsed = unparsed
                    )
                }

                syncMetaRepository.upsert(
                    SyncMeta(
                        lastSyncTimestamp = System.currentTimeMillis(),
                        lastSmsId = null
                    )
                )

                SyncResult(scanned = total, inserted = inserted, unparsed = unparsed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult(error = "Sync failed: ${e.message ?: "unknown error"}")
        } finally {
            isRunning = false
        }
    }
}
```

- [ ] **Step 5: Delete the stub and run tests**

Delete `domain/usecase/SyncSmsUseCase.kt`.

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all `SmsSyncUseCaseTest` tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain app/src/test/java/com/smsexpensetracker/domain
git commit -m "feat: add SmsSyncUseCase with dedup insert and parse-log recording; remove SyncSmsUseCase stub"
```

---

## Task 3: DI — SmsReader, ParseLogRepository, SyncMetaRepository

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/di/SmsModule.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/di/RepositoryModule.kt`

**Interfaces:**
- Consumes: `SmsReader(ContentResolver)`, `ParseLogRepositoryImpl(ParseLogDao)`, `SyncMetaRepositoryImpl(SyncMetaDao)`.
- Produces: Hilt bindings so `SmsSyncUseCase` (and later the Worker/VM) can be injected. `ParseLogDao`/`SyncMetaDao` already provided in `DatabaseModule`.

- [ ] **Step 1: Create `SmsModule`**

```kotlin
package com.smsexpensetracker.di

import android.content.Context
import com.smsexpensetracker.data.sms.SmsReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SmsModule {
    @Provides
    @Singleton
    fun provideSmsReader(@ApplicationContext context: Context): SmsReader =
        SmsReader(context.contentResolver)
}
```

- [ ] **Step 2: Bind the missing repositories**

In `RepositoryModule.kt`, add imports for `ParseLogRepositoryImpl`, `SyncMetaRepositoryImpl`, `ParseLogRepository`, `SyncMetaRepository` and two `@Binds`:

```kotlin
@Binds
@Singleton
abstract fun bindParseLogRepository(impl: ParseLogRepositoryImpl): ParseLogRepository

@Binds
@Singleton
abstract fun bindSyncMetaRepository(impl: SyncMetaRepositoryImpl): SyncMetaRepository
```

- [ ] **Step 3: Build to verify the graph**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/di
git commit -m "feat: add Hilt bindings for SmsReader, ParseLogRepository, SyncMetaRepository"
```

---

## Task 4: `PermissionManager`

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/data/sms/PermissionManager.kt`

**Interfaces:**
- Consumes: nothing (thin Android-framework wrapper).
- Produces:
  - `fun hasPermission(context: Context): Boolean`
  - `fun shouldShowRationale(activity: Activity?): Boolean`
  - `fun openSettings(context: Context)`

> **Test decision (documented):** this class is a thin adapter over `ContextCompat`/`ActivityCompat`/`Settings`. The project has no Robolectric, so these framework calls can't be JVM-tested. It ships without a unit test and is covered by the Task 5 manual smoke test. If you want it testable, extract an interface and mock it — but that adds indirection for zero logic. Flag this decision to the user during review.

- [ ] **Step 1: Implement `PermissionManager`**

```kotlin
package com.smsexpensetracker.data.sms

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun shouldShowRationale(activity: Activity?): Boolean =
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.READ_SMS
        )

    fun openSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/data/sms/PermissionManager.kt
git commit -m "feat: add PermissionManager for on-demand READ_SMS flow"
```

---

## Task 5: Transactions screen sync trigger + permission flow

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModelTest.kt`

**Interfaces:**
- Consumes: `SmsSyncUseCase.sync(): SyncResult` (Task 2), `PermissionManager` (Task 4).
- Produces: `TransactionsViewModel.sync()`, `TransactionsUiState.isSyncing: Boolean`, `TransactionsUiState.syncMessage: String?`, `TransactionsViewModel.consumeSyncMessage()`.

- [ ] **Step 1: Add sync to the ViewModel**

In `TransactionsViewModel.kt`:
1. Inject `SmsSyncUseCase` (add `private val smsSyncUseCase: SmsSyncUseCase` to the constructor).
2. Add fields to `TransactionsUiState`: `isSyncing: Boolean = false`, `syncMessage: String? = null`.
3. Add two `MutableStateFlow`s:

```kotlin
private val _isSyncing = MutableStateFlow(false)
private val _syncMessage = MutableStateFlow<String?>(null)
```

4. Add both to the `combine` source list (append at the end, so existing indices 0–6 are untouched) and read them in the combine block:

```kotlin
val isSyncing = array[7] as Boolean
val syncMessage = array[8] as String?
```

and set `isSyncing = isSyncing, syncMessage = syncMessage` on the emitted `TransactionsUiState`.

5. Add:

```kotlin
fun sync() {
    if (_isSyncing.value) return
    _isSyncing.value = true
    viewModelScope.launch {
        val result = smsSyncUseCase.sync()
        _syncMessage.value = if (result.error != null) {
            "Sync failed. Try again."
        } else {
            "Scanned ${result.scanned}, added ${result.inserted}, unparsed ${result.unparsed}"
        }
        _isSyncing.value = false
    }
}

fun consumeSyncMessage() {
    _syncMessage.value = null
}
```

- [ ] **Step 2: Update the existing ViewModel test to the new constructor**

In `TransactionsViewModelTest.kt`:
1. Add `private lateinit var smsSyncUseCase: SmsSyncUseCase` and mock it in `setup()`.
2. Replace every `TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository)` with `TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase)`.
3. Add a new test:

```kotlin
@Test
fun `sync runs use case and publishes result message`() = runTest(testDispatcher) {
    coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
    every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
    every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
    coEvery { smsSyncUseCase.sync() } returns SyncResult(scanned = 5, inserted = 2, unparsed = 1)

    viewModel = TransactionsViewModel(
        getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase
    )
    backgroundScope.launch { viewModel.uiState.collect { } }
    advanceUntilIdle()

    viewModel.sync()
    advanceUntilIdle()

    val msg = viewModel.uiState.value.syncMessage
    assertTrue(msg != null && msg.contains("Scanned 5"))
    assertTrue(!viewModel.uiState.value.isSyncing)

    viewModel.consumeSyncMessage()
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.syncMessage == null)
}
```

Add `import com.smsexpensetracker.domain.usecase.SmsSyncUseCase` and `import com.smsexpensetracker.domain.value.SyncResult`.

- [ ] **Step 3: Wire the screen — snackbar, permission flow, sync button**

In `TransactionsScreen.kt`:
1. Add imports:

```kotlin
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.smsexpensetracker.data.sms.PermissionManager
import kotlinx.coroutines.launch
```

2. At the top of `TransactionsScreen`, add:

```kotlin
val context = LocalContext.current
val scope = rememberCoroutineScope()
val snackbarHostState = remember { SnackbarHostState() }
val permissionManager = remember { PermissionManager() }
var showRationale by remember { mutableStateOf(false) }

val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) {
        viewModel.sync()
    } else {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "SMS access is needed to sync transactions",
                actionLabel = "Open Settings",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                permissionManager.openSettings(context)
            }
        }
    }
}

fun beginSync() {
    if (permissionManager.hasPermission(context)) {
        viewModel.sync()
    } else if (permissionManager.shouldShowRationale(context as? Activity)) {
        showRationale = true
    } else {
        permissionLauncher.launch(Manifest.permission.READ_SMS)
    }
}
```

3. Add a `snackbarHost` to the existing `Scaffold` and consume the result message:

```kotlin
snackbarHost = { SnackbarHost(snackbarHostState) }
```

```kotlin
LaunchedEffect(state.syncMessage) {
    val message = state.syncMessage
    if (message != null) {
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSyncMessage()
    }
}
```

4. Add the rationale dialog (place after the `Scaffold`, or inside it):

```kotlin
if (showRationale) {
    AlertDialog(
        onDismissRequest = { showRationale = false },
        title = { Text("Allow SMS access?") },
        text = { Text("SMS Expense Tracker reads your bank SMS to extract transaction details. SMS data stays on your device.") },
        confirmButton = {
            TextButton(onClick = {
                showRationale = false
                permissionLauncher.launch(Manifest.permission.READ_SMS)
            }) { Text("Allow") }
        },
        dismissButton = {
            TextButton(onClick = { showRationale = false }) { Text("Not now") }
        }
    )
}
```

5. Make the sync action always reachable: wrap the search bar (currently `item(key = "search") { TransactionSearchBar(...) }`) in a `Row` with a sync `IconButton`. `TransactionSearchBar` already applies its own `padding(horizontal = 16.dp)` internally (`TransactionSearchBar.kt:47`), so the Row must NOT add horizontal padding again:

```kotlin
item(key = "search") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransactionSearchBar(
            query = state.searchQuery,
            onQueryChange = viewModel::onSearchQueryChange,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { beginSync() },
            enabled = !state.isSyncing,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            if (state.isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = "Sync SMS")
            }
        }
    }
}
```

6. Wire the empty-state button (currently `onAction = { /* TODO: trigger sync ... */ }`) to `beginSync()`.

- [ ] **Step 4: Run tests and build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL; all tests pass (including updated `TransactionsViewModelTest`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui app/src/test/java/com/smsexpensetracker/ui
git commit -m "feat: add sync trigger with permission flow and result snackbar to Transactions screen"
```

---

## Task 6: Update TODO.md

**Files:**
- Modify: `TODO.md`

- [ ] **Step 1: Mark completed items**

In `TODO.md`:
- Task 4 (Phase 1): mark the `ParseLog` recording line `[x]` (FAILED logs now recorded during sync).
- Task 6 (Phase 2): mark `PermissionManager` line `[x]`.
- Task 7 (Phase 2): mark `SmsSyncUseCase` orchestration, dedup via `smsBodyHash`, and end-to-end verify as `[x]`/`[-]`; leave `SmsSyncWorker` and incremental-sync lines `[ ]` with a note "(deferred — full-scan + hash-dedup chosen; background worker is a separate sub-project)".

- [ ] **Step 2: Commit**

```bash
git add TODO.md
git commit -m "docs: update TODO after manual sync core (Tasks 4, 6, 7 partial)"
```

---

## Self-Review Checklist

- [ ] **Spec coverage:** permission (Task 4/5), full-scan + hash-dedup (Task 1/2), `SmsSyncUseCase` orchestration + progress (Task 2), `ParseLog(FAILED)` recording (Task 2), `SyncMeta` upsert (Task 2), result snackbar + spinner (Task 5), error handling incl. rethrown cancellation (Task 2), tests (Tasks 1, 2, 5), DI (Task 3).
- [ ] **Placeholder scan:** no TBD/TODO in plan code; only the empty-state TODO comment being replaced is intentional.
- [ ] **Type consistency:** `SyncResult(scanned, inserted, unparsed, error)`, `SyncProgress(processed, total, unparsed)`, `insertBatch(List<Transaction>): Int`, `insertBatchIgnore(...): LongArray`, `smsBodyHash: String?` used identically across Tasks 1, 2, 5.
