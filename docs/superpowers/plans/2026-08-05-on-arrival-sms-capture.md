# On-Arrival SMS Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse and record a bank SMS automatically the moment it arrives, via a manifest `BroadcastReceiver`, so users never need to tap "Sync SMS".

**Architecture:** An `SMS_RECEIVED_ACTION` receiver extracts body/sender/timestamp, then delegates to a new `SmsSyncUseCase.handleIncomingSms(body, sender, timestamp)`. That method shares a parse/record helper with the existing full-scan `sync()` so the two paths can't drift, bails silently for non-bank senders, and relies on the existing `smsBodyHash` unique index for idempotency. The permission flow is upgraded to request both `READ_SMS` and `RECEIVE_SMS`.

**Tech Stack:** Kotlin, Android BroadcastReceiver, Hilt (`@AndroidEntryPoint`), coroutines, Room (existing), JUnit 4 + MockK + `runTest`.

## Global Constraints

- Package: `com.smsexpensetracker`; min SDK 28 / target 36 / compile 37.
- **No new dependencies** — WorkManager stays out; receiver runs in-process via `goAsync()`.
- Money is paisa `Long`; never `Double`/`BigDecimal`.
- TDD: write the failing test first, verify it fails, then implement.
- Test gate: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug` — 389 existing tests must stay green, plus new ones.
- `CancellationException` is always re-thrown (existing convention). Everything else in a background path is caught, logged via `Timber` tag `"PARSE"`, and never crashes the process.
- Commit per task, conventional messages (`feat:`/`docs:`) matching repo history.

---

## File Structure

- **Modify** `app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt` — add `BankRepository` dep, extract private `classifySms(...)` helper, add public `handleIncomingSms(...)`; refactor `sync()` to use the helper (behavior-preserving).
- **Modify** `app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt` — constructor gains `bankRepository`; 6 new `handleIncomingSms` tests.
- **Modify** `app/src/main/java/com/smsexpensetracker/data/sms/PermissionManager.kt` — `hasPermission` checks both permissions.
- **Modify** `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt` — request both permissions.
- **Modify** `app/src/main/java/com/smsexpensetracker/di/SmsModule.kt` — provide app `CoroutineScope`.
- **Create** `app/src/main/java/com/smsexpensetracker/data/sms/SmsIncomingReceiver.kt` — thin receiver.
- **Modify** `app/src/main/AndroidManifest.xml` — register the receiver.

---

### Task 1: `handleIncomingSms` on `SmsSyncUseCase` (shared helper refactor)

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt`
- Test: `app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt`

**Interfaces:**
- Consumes: `BankRepository.getAllBanks(): Flow<List<Bank>>`, `detectBankForSender(sender: String, banks: List<Bank>): Long?` (`core.parser`), existing `SmsSyncUseCase` deps.
- Produces: `suspend fun SmsSyncUseCase.handleIncomingSms(body: String, sender: String, timestamp: Long): Boolean` — `true` iff a transaction was inserted. Also produces `private suspend fun classifySms(body, sender, timestamp, rulePairs): ClassifyResult` and private sealed `ClassifyResult { TransactionReady(Transaction) | ParseFailed | Skipped }`, used by Task 3's receiver and the refactored `sync()`.

- [ ] **Step 1: Add the failing tests**

Add `import com.smsexpensetracker.domain.model.Bank` and `import com.smsexpensetracker.domain.model.ParseMethod` to `SmsSyncUseCaseTest.kt`, then replace the field declarations and `setup()`:

```kotlin
    private lateinit var bankRepository: BankRepository
    // ... existing fields ...

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        smsReader = mockk()
        smsRuleRepository = mockk()
        transactionRepository = mockk()
        parseLogRepository = mockk()
        syncMetaRepository = mockk()
        bankRepository = mockk()
        demoDataPreferences = mockk()
        every { demoDataPreferences.demoDataLoaded } returns flowOf(false)
        every { bankRepository.getAllBanks() } returns flowOf(listOf(Bank(1L, "HDFC Bank", "HDFCBK")))
        useCase = SmsSyncUseCase(
            smsReader,
            smsRuleRepository,
            transactionRepository,
            parseLogRepository,
            syncMetaRepository,
            bankRepository,
            demoDataPreferences,
            testDispatcher
        )
    }
```

Add `import com.smsexpensetracker.domain.repository.BankRepository` and these six tests to the class:

```kotlin
    @Test
    fun `handleIncomingSms returns false when demo data is loaded`() = runTest {
        every { demoDataPreferences.demoDataLoaded } returns MutableStateFlow(true)

        val result = useCase.handleIncomingSms(hdfcSms.body, hdfcSms.sender, hdfcSms.timestamp)

        assertEquals(false, result)
        coVerify(exactly = 0) { bankRepository.getAllBanks() }
        coVerify(exactly = 0) { transactionRepository.insertBatch(any()) }
    }

    @Test
    fun `handleIncomingSms ignores non-bank sender without a parse log`() = runTest {
        val result = useCase.handleIncomingSms("Your OTP is 1234", "VM-OTPSVC", System.currentTimeMillis())

        assertEquals(false, result)
        coVerify(exactly = 0) { parseLogRepository.insert(any()) }
        coVerify(exactly = 0) { transactionRepository.insertBatch(any()) }
        coVerify(exactly = 0) { syncMetaRepository.upsert(any()) }
    }

    @Test
    fun `handleIncomingSms inserts transaction for bank sms`() = runTest {
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatch(any()) } returns 1
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.handleIncomingSms(hdfcSms.body, hdfcSms.sender, hdfcSms.timestamp)

        assertEquals(true, result)
        coVerify {
            transactionRepository.insertBatch(
                match { list ->
                    list.size == 1 &&
                        list[0].amount == 483176L &&
                        list[0].bankId == 1L &&
                        list[0].smsTimestamp == hdfcSms.timestamp &&
                        list[0].parseMethod == ParseMethod.SMS
                }
            )
        }
        coVerify(exactly = 0) { parseLogRepository.insert(any()) }
        coVerify(exactly = 1) { syncMetaRepository.upsert(any()) }
    }

    @Test
    fun `handleIncomingSms records failed parse log and returns false`() = runTest {
        val body = "Rs. 100 debited from A/c for something"
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(emptyList())
        coEvery { parseLogRepository.insert(any()) } returns Unit

        val result = useCase.handleIncomingSms(body, "AD-HDFCBK-S", 1750000000000L)

        assertEquals(false, result)
        coVerify {
            parseLogRepository.insert(
                match { log -> log.status == ParseStatus.FAILED && log.smsBody == body }
            )
        }
        coVerify(exactly = 0) { transactionRepository.insertBatch(any()) }
        coVerify(exactly = 0) { syncMetaRepository.upsert(any()) }
    }

    @Test
    fun `handleIncomingSms returns false when insert is deduplicated`() = runTest {
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatch(any()) } returns 0

        val result = useCase.handleIncomingSms(hdfcSms.body, hdfcSms.sender, hdfcSms.timestamp)

        assertEquals(false, result)
        coVerify(exactly = 0) { syncMetaRepository.upsert(any()) }
    }

    @Test
    fun `handleIncomingSms returns false when repository throws`() = runTest {
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatch(any()) } throws RuntimeException("db down")

        val result = useCase.handleIncomingSms(hdfcSms.body, hdfcSms.sender, hdfcSms.timestamp)

        assertEquals(false, result)
    }
```

- [ ] **Step 2: Run tests to verify the new ones fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.SmsSyncUseCaseTest" --console=plain`
Expected: 6 new tests FAIL ("unresolved reference: handleIncomingSms"), existing tests FAIL too because the constructor now has an extra positional arg — expected at this stage.

- [ ] **Step 3: Add `BankRepository` and the private helper**

Add imports to `SmsSyncUseCase.kt`:

```kotlin
import com.smsexpensetracker.core.parser.detectBankForSender
import com.smsexpensetracker.domain.repository.BankRepository
```

Add `bankRepository` to the constructor (before `demoDataPreferences`):

```kotlin
class SmsSyncUseCase @Inject constructor(
    private val smsReader: SmsReader,
    private val smsRuleRepository: SmsRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val parseLogRepository: ParseLogRepository,
    private val syncMetaRepository: SyncMetaRepository,
    private val bankRepository: BankRepository,
    private val demoDataPreferences: DemoDataPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
)
```

At the bottom of the class, add the sealed result type (file-private, above the class) and the shared helper:

```kotlin
private sealed interface ClassifyResult {
    data class TransactionReady(val transaction: Transaction) : ClassifyResult
    data object ParseFailed : ClassifyResult
    data object Skipped : ClassifyResult
}
```

```kotlin
    private suspend fun classifySms(
        body: String,
        sender: String,
        timestamp: Long,
        rulePairs: List<Pair<Long, String>>
    ): ClassifyResult {
        val parsed = ParserEngine.parse(body, sender, rulePairs)
        if (parsed.errorMessage != null) {
            Timber.tag("PARSE").w("Parse failed [$sender]: ${parsed.errorMessage}")
            parseLogRepository.insert(
                ParseLog(
                    id = 0L,
                    smsBody = body,
                    smsSender = sender,
                    parsedAt = LocalDateTime.now(),
                    status = ParseStatus.FAILED,
                    errorMessage = parsed.errorMessage
                )
            )
            return ClassifyResult.ParseFailed
        }
        if (parsed.bankId != null && parsed.amount > 0L) {
            return ClassifyResult.TransactionReady(
                Transaction(
                    id = 0L,
                    bankId = parsed.bankId,
                    amount = parsed.amount,
                    transactionType = parsed.type,
                    description = parsed.description,
                    transactionDate = LocalDate.now().atStartOfDay(),
                    categoryId = null,
                    rawSms = body,
                    smsTimestamp = timestamp,
                    createdAt = LocalDateTime.now(),
                    parseMethod = ParseMethod.SMS
                )
            )
        }
        return ClassifyResult.Skipped
    }
```

- [ ] **Step 4: Refactor `sync()` to use the helper (behavior-preserving)**

Replace the inner `for (msg in chunk) { ... }` block in `sync()`:

```kotlin
            messages.chunked(100).forEach { chunk ->
                val transactions = mutableListOf<Transaction>()
                for (msg in chunk) {
                    when (val result = classifySms(msg.body, msg.sender, msg.timestamp, rulePairs)) {
                        is ClassifyResult.TransactionReady -> transactions += result.transaction
                        ClassifyResult.ParseFailed -> unparsed++
                        ClassifyResult.Skipped -> Unit
                    }
                    processed++
                }
                if (transactions.isNotEmpty()) {
                    inserted += transactionRepository.insertBatch(transactions)
                }
                _progress.value = SyncProgress(processed = processed, total = total, unparsed = unparsed)
            }
```

and before it, build `rulePairs` once:

```kotlin
            val rules = smsRuleRepository.getAllRules().first().filter { it.isActive }
            val rulePairs = rules.map { it.bankId to it.pattern }
```

(Remove the now-unused `rules.map { it.bankId to it.pattern }` from inside the loop.)

- [ ] **Step 5: Add `handleIncomingSms`**

Add to the class (after `sync()`):

```kotlin
    suspend fun handleIncomingSms(body: String, sender: String, timestamp: Long): Boolean {
        try {
            if (demoDataPreferences.demoDataLoaded.first()) return false
            val banks = bankRepository.getAllBanks().first()
            if (detectBankForSender(sender, banks) == null) return false

            val rulePairs = smsRuleRepository.getAllRules().first()
                .filter { it.isActive }
                .map { it.bankId to it.pattern }

            val inserted = when (val result = classifySms(body, sender, timestamp, rulePairs)) {
                is ClassifyResult.TransactionReady ->
                    transactionRepository.insertBatch(listOf(result.transaction))
                ClassifyResult.ParseFailed, ClassifyResult.Skipped -> 0
            }

            if (inserted > 0) {
                syncMetaRepository.upsert(
                    SyncMeta(lastSyncTimestamp = System.currentTimeMillis(), lastSmsId = null)
                )
            }
            return inserted > 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("PARSE").e(e, "handleIncomingSms failed")
            return false
        }
    }
```

- [ ] **Step 6: Run the full test suite**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest --console=plain`
Expected: ALL pass — the 6 new tests plus the existing 8 `sync()` tests (refactor is behavior-preserving). Total suite stays green (389 + 6 = 395).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt
git commit -m "feat(sms): add handleIncomingSms single-message path sharing parse logic with sync"
```

---

### Task 2: Request `READ_SMS` + `RECEIVE_SMS` in the permission flow

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/data/sms/PermissionManager.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `PermissionManager.hasPermission(context): Boolean` now requires **both** permissions; `TransactionsScreen` requests both via `RequestMultiplePermissions`. Task 3's receiver silently does nothing until `RECEIVE_SMS` is granted.

- [ ] **Step 1: Update `PermissionManager.hasPermission`**

Replace the single-permission check with a both-permissions check:

```kotlin
    fun hasPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.READ_SMS) &&
            hasPermission(context, Manifest.permission.RECEIVE_SMS)

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
```

- [ ] **Step 2: Update `TransactionsScreen` launcher**

Replace the `RequestPermission()` launcher (`~line 83-100`) with a multiple-permission one:

```kotlin
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants[Manifest.permission.READ_SMS] == true &&
            grants[Manifest.permission.RECEIVE_SMS] == true
        if (allGranted) {
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
```

and in `beginSync()`, launch with both:

```kotlin
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
            )
```

- [ ] **Step 3: Verify it compiles and tests stay green**

Run: `./gradlew assembleDebug testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL; all unit tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/data/sms/PermissionManager.kt app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt
git commit -m "feat(permissions): request RECEIVE_SMS alongside READ_SMS for on-arrival capture"
```

---

### Task 3: App coroutine scope, receiver, and manifest registration

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/di/SmsModule.kt`
- Create: `app/src/main/java/com/smsexpensetracker/data/sms/SmsIncomingReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `SmsSyncUseCase.handleIncomingSms(body, sender, timestamp)` (Task 1); Hilt app scope.
- Produces: manifest-registered `SmsIncomingReceiver` for `Telephony.Sms.Intents.SMS_RECEIVED_ACTION` that delegates to the use case and always calls `pendingResult.finish()`.

- [ ] **Step 1: Provide the app `CoroutineScope`**

Add to `di/SmsModule.kt` (plus imports `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.SupervisorJob`):

```kotlin
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

- [ ] **Step 2: Create `SmsIncomingReceiver`**

Create `data/sms/SmsIncomingReceiver.kt`:

```kotlin
package com.smsexpensetracker.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsIncomingReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsSyncUseCase: SmsSyncUseCase

    @Inject
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        if (body.isBlank()) return

        val pendingResult = goAsync()
        appScope.launch {
            try {
                smsSyncUseCase.handleIncomingSms(body, sender, timestamp)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 3: Register the receiver in the manifest**

Add inside `<application>` (after the `<provider>` block) in `AndroidManifest.xml`:

```xml
        <receiver
            android:name=".data.sms.SmsIncomingReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.provider.Telephony.SMS_RECEIVED_ACTION" />
            </intent-filter>
        </receiver>
```

`exported="true"` is required because the system (not the app) sends this broadcast.

- [ ] **Step 4: Verify the full gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL; all 395 tests pass. (Hilt `@AndroidEntryPoint` on a `BroadcastReceiver` is supported by Hilt 2.60.1.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/di/SmsModule.kt app/src/main/java/com/smsexpensetracker/data/sms/SmsIncomingReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat(sms): add SMS_RECEIVED receiver that captures bank sms on arrival"
```

---

## Self-Review

**1. Spec coverage:**
- §4.1 receiver → Task 3 ✓
- §4.2 app scope → Task 3 ✓
- §4.3 `handleIncomingSms` + shared helper → Task 1 ✓
- §4.4 both permissions → Task 2 ✓
- §5 sender bail (no ParseLog) → Task 1 (`detectBankForSender` check before any parse) ✓
- §6 demo-data gate → Task 1 (first check in `handleIncomingSms`) ✓
- §7 error handling (`finish()` in `finally`, CancellationException rethrow, catch→false) → Tasks 1 & 3 ✓
- §8 tests (6 new) → Task 1 ✓; manual device check folded into F1, noted in Task 3 ✓
- §9 files → all listed ✓

**2. Placeholder scan:** No TBD/TODO/`similar to`/vague steps — every step has concrete code and an exact run command with expected outcome.

**3. Type consistency:**
- `handleIncomingSms(body: String, sender: String, timestamp: Long): Boolean` — defined in Task 1, consumed in Task 3 with matching arg types ✓
- Constructor arg order `(smsReader, smsRuleRepository, transactionRepository, parseLogRepository, syncMetaRepository, bankRepository, demoDataPreferences, ioDispatcher)` — Task 1 test setup and Task 1 implementation match ✓
- `detectBankForSender(sender, banks): Long?` matches `SenderDetector.kt:19` ✓
- `ClassifyResult` (TransactionReady/ParseFailed/Skipped) — used identically in `sync()` and `handleIncomingSms` ✓
- `insertBatch(listOf(tx))` returns `Int` — used as `inserted > 0` ✓
- `Bank(id, name, smsSender)` matches `Bank.kt` ✓

**Manual device verification (F1 follow-up):** install on device, grant both permissions, `adb emu sms send <number> "<bank sms>"` with the app closed → reopen Transactions → the transaction is present without tapping Sync.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-05-on-arrival-sms-capture.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
