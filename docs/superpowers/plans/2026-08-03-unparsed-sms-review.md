# Unparsed SMS Review Screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Settings→"Unparsed SMS" screen where every SMS that failed to parse is readable (full body, deduped), fixable via the existing Rule Editor pre-filled with the SMS body and auto-detected bank, and re-testable with a "Re-sync now" action that clears stale FAILED logs before re-running sync.

**Architecture:** A new `UnparsedSmsViewModel` combines the existing `ParseLogRepository.getAllLogs()` Flow with `BankRepository.getAllBanks()` to derive a deduped `failedLogs` list (one `FailedSms` per unique body, `failCount`, newest `lastParsedAt`, `bankId` via a newly-extracted shared `detectBankForSender`). The Rule Editor gains an optional `sampleSms` nav arg so the "Fix" button opens it pre-filled. Re-sync deletes FAILED logs (`ParseLogDao.deleteFailed()`) then runs the existing `SmsSyncUseCase.sync()`. No changes to `SmsSyncUseCase`, the parser stack, or the Logs screen.

**Tech Stack:** Kotlin, Room (Flow), Hilt, Compose Material 3, Navigation Compose, MockK + `kotlinx-coroutines-test` (JUnit 4).

## Global Constraints

- Package root: `com.smsexpensetracker`. Money is paisa `Long`. No code comments unless asked.
- Build gate (no lint/typecheck configured): `./gradlew assembleDebug` and `./gradlew cleanTestDebugUnitTest testDebugUnitTest`.
- Test baseline: 310 tests green. JUnit 4, MockK (ByteBuddy agent warnings harmless), `runTest { }` + `StandardTestDispatcher` + `Dispatchers.setMain` for ViewModels.
- `ParseLogRepository.getAllLogs()` (`ParseLogDao.getAllLogs`, `ORDER BY parsedAt DESC`) is the **single** data source for the review screen — filter/dedupe in memory, no `getFailedLogs()` query.
- Sender→bank detection must stay consistent with `ParserViewModel.detectBank` — it becomes a thin delegate to the shared function.
- Re-sync = `deleteFailed()` **then** `sync()`. Old FAILED history is intentionally wiped (user-approved).
- Settings row label exactly **"Unparsed SMS"**, placed directly above the existing "Logs" row.
- No inline rule editor — reuse the existing Rule Editor composable. No auto re-sync on rule save.

---

## File Structure Map

| File | Change | Responsibility |
|---|---|---|
| `app/src/main/java/com/smsexpensetracker/core/parser/SenderDetector.kt` | Modify | Add top-level `detectBankForSender(sender, banks): Long?` |
| `app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserViewModel.kt` | Modify | `detectBank` delegates to `detectBankForSender` |
| `app/src/test/java/com/smsexpensetracker/core/parser/DetectBankForSenderTest.kt` | Create | Unit tests for the shared detection |
| `app/src/main/java/com/smsexpensetracker/core/database/dao/ParseLogDao.kt` | Modify | Add `deleteFailed()` |
| `app/src/main/java/com/smsexpensetracker/domain/repository/ParseLogRepository.kt` | Modify | Add `suspend fun deleteFailed()` |
| `app/src/main/java/com/smsexpensetracker/data/repository/ParseLogRepositoryImpl.kt` | Modify | Delegate `deleteFailed()` to DAO |
| `app/src/test/java/com/smsexpensetracker/data/repository/ParseLogRepositoryImplTest.kt` | Modify | Add `deleteFailed` delegation test |
| `app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModel.kt` | Modify | Pre-fill `sampleSms` from `SavedStateHandle` |
| `app/src/test/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModelTest.kt` | Modify | Add `sampleSms` prefill test |
| `app/src/main/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsViewModel.kt` | Create | `FailedSms`, `UnparsedFilter`, `UnparsedSmsUiState`, dedup + resync logic |
| `app/src/test/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsViewModelTest.kt` | Create | ViewModel unit tests |
| `app/src/main/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsScreen.kt` | Create | Compose review screen |
| `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt` | Modify | Rule-edit route gains `sampleSms` arg (Task 3); `unparsed_sms` route + Settings wiring (Task 6) |
| `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt` | Modify | Add `onNavigateToUnparsedSms` param + "Unparsed SMS" row |
| `TESTING.md`, `TODO.md` | Modify | Manual QA section + task checklist |

---

### Task 1: Shared sender→bank detection

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/parser/SenderDetector.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/core/parser/DetectBankForSenderTest.kt` (create)

**Interfaces:**
- Consumes: `Bank` (`com.smsexpensetracker.domain.model.Bank`), `SenderId.value` (existing).
- Produces: top-level `fun detectBankForSender(sender: String, banks: List<Bank>): Long?` in package `com.smsexpensetracker.core.parser`. Used by Task 4.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/core/parser/DetectBankForSenderTest.kt`:

```kotlin
package com.smsexpensetracker.core.parser

import com.smsexpensetracker.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectBankForSenderTest {

    private val hdfc = Bank(id = 1L, name = "HDFC Bank", smsSender = "HDFCBK")
    private val icici = Bank(id = 2L, name = "ICICI Bank", smsSender = "ICICIB")
    private val banks = listOf(hdfc, icici)

    @Test
    fun `exact sender match returns bank id`() {
        assertEquals(1L, detectBankForSender("HDFCBK", banks))
    }

    @Test
    fun `TRAI prefixed sender resolves to bank`() {
        assertEquals(1L, detectBankForSender("AD-HDFCBK-S", banks))
        assertEquals(2L, detectBankForSender("AD-ICICIB-S", banks))
    }

    @Test
    fun `sender containing bank code matches`() {
        assertEquals(1L, detectBankForSender("XXHDFCBKXX", banks))
    }

    @Test
    fun `bank code contained in longer sender matches`() {
        assertEquals(1L, detectBankForSender("HDFCBK-EXTRA", banks))
    }

    @Test
    fun `unknown sender returns null`() {
        assertNull(detectBankForSender("UNKNOWN", banks))
    }

    @Test
    fun `blank sender returns null`() {
        assertNull(detectBankForSender("   ", banks))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.parser.DetectBankForSenderTest"`
Expected: FAIL — `detectBankForSender` not defined.

- [ ] **Step 3: Add the shared function**

Append to `SenderDetector.kt` (keep the existing `object SenderDetector` and `cleanTraiPrefix`):

```kotlin
package com.smsexpensetracker.core.parser

import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.SenderId

object SenderDetector {
    fun detect(sender: String): SenderId {
        val cleaned = cleanTraiPrefix(sender)
        return SenderId(cleaned)
    }

    private fun cleanTraiPrefix(raw: String): String {
        val parts = raw.split("-")
        return parts.firstOrNull { it.length >= 3 && it.all { c -> c.isLetterOrDigit() } }
            ?: raw
    }
}

fun detectBankForSender(sender: String, banks: List<Bank>): Long? {
    val cleaned = SenderDetector.detect(sender).value.uppercase()
    if (cleaned.isBlank()) return null
    return banks.firstOrNull { bank ->
        val smsSender = bank.smsSender.uppercase()
        cleaned == smsSender || cleaned.contains(smsSender) || smsSender.contains(cleaned)
    }?.id
}
```

(That is: add the `Bank` import and the top-level function below the object.)

- [ ] **Step 4: Make ParserViewModel delegate**

In `ParserViewModel.kt`, add `import com.smsexpensetracker.core.parser.detectBankForSender` and replace the body of `detectBank` (currently `ParserViewModel.kt:75-82`):

```kotlin
fun detectBank(sender: String, banks: List<Bank>): Long? = detectBankForSender(sender, banks)
```

The existing `ParserViewModelTest.detectBank resolves TRAI sender to bank id` (`ParserViewModelTest.kt:131-135`) keeps passing because it calls the delegate.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.parser.DetectBankForSenderTest" --tests "com.smsexpensetracker.ui.screens.parser.ParserViewModelTest"`
Expected: PASS (both classes).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/parser/SenderDetector.kt app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserViewModel.kt app/src/test/java/com/smsexpensetracker/core/parser/DetectBankForSenderTest.kt
git commit -m "feat(parser): share sender-to-bank detection"
```

---

### Task 2: Data layer — `deleteFailed()`

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/dao/ParseLogDao.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/domain/repository/ParseLogRepository.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/ParseLogRepositoryImpl.kt`
- Test: `app/src/test/java/com/smsexpensetracker/data/repository/ParseLogRepositoryImplTest.kt`

**Interfaces:**
- Consumes: existing `ParseLogDao`, `ParseLogRepository`.
- Produces: `ParseLogRepository.deleteFailed(): suspend () -> Unit` (DAO: `@Query("DELETE FROM parse_logs WHERE status = 'FAILED'") suspend fun deleteFailed()`). Consumed by Task 4 (`resync()`).

- [ ] **Step 1: Write the failing test**

Add to `ParseLogRepositoryImplTest.kt`:

```kotlin
@Test
fun `deleteFailed delegates to dao`() = runTest {
    coEvery { parseLogDao.deleteFailed() } returns Unit

    repo.deleteFailed()

    coVerify { parseLogDao.deleteFailed() }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.repository.ParseLogRepositoryImplTest"`
Expected: FAIL — `repo.deleteFailed()` does not exist (compile error).

- [ ] **Step 3: Implement the three additions**

`ParseLogDao.kt` — add below `getAllLogs()`:

```kotlin
@Query("DELETE FROM parse_logs WHERE status = 'FAILED'")
suspend fun deleteFailed()
```

`ParseLogRepository.kt` — add to the interface:

```kotlin
suspend fun deleteFailed()
```

`ParseLogRepositoryImpl.kt` — add:

```kotlin
override suspend fun deleteFailed() = parseLogDao.deleteFailed()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.repository.ParseLogRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/database/dao/ParseLogDao.kt app/src/main/java/com/smsexpensetracker/domain/repository/ParseLogRepository.kt app/src/main/java/com/smsexpensetracker/data/repository/ParseLogRepositoryImpl.kt app/src/test/java/com/smsexpensetracker/data/repository/ParseLogRepositoryImplTest.kt
git commit -m "feat(parse-log): add deleteFailed to clear stale failures"
```

---

### Task 3: Rule Editor `sampleSms` prefill + nav arg

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModel.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `SavedStateHandle` already injected into `RuleEditorViewModel`; nav route `"banks/{bankId}/rules/edit?ruleId={ruleId}"`.
- Produces: rule-edit route becomes `"banks/{bankId}/rules/edit?ruleId={ruleId}&sampleSms={sampleSms}"` (`sampleSms` optional `String`, default `""`); `RuleEditorUiState.sampleSms` starts non-blank when the arg is present. Consumed by Task 6's "Fix" navigation.

- [ ] **Step 1: Write the failing test**

Add to `RuleEditorViewModelTest.kt`:

```kotlin
@Test
fun `sampleSms nav arg pre-fills the sample sms field`() = runTest(testDispatcher) {
    coEvery { bankRepository.getBankById(1L) } returns hdfc
    val vm = viewModel(mapOf("bankId" to 1L, "ruleId" to -1L, "sampleSms" to smsBody))
    advanceUntilIdle()
    assertEquals(smsBody, vm.uiState.value.sampleSms)
}
```

`viewModel(savedState)` helper at `RuleEditorViewModelTest.kt:56-58` already builds `RuleEditorViewModel(SavedStateHandle(savedState), ...)`, so the arg flows through untouched.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.banks.RuleEditorViewModelTest"`
Expected: FAIL — `sampleSms` is `""`.

- [ ] **Step 3: Pre-fill from SavedStateHandle**

In `RuleEditorViewModel.kt`, initialize the state flow from the nav arg. Replace:

```kotlin
    private val _uiState = MutableStateFlow(RuleEditorUiState())
```

with:

```kotlin
    private val _uiState = MutableStateFlow(
        RuleEditorUiState(sampleSms = savedStateHandle.get<String>("sampleSms").orEmpty())
    )
```

The existing `add mode starts with empty fields` test (`RuleEditorViewModelTest.kt:70-81`) still passes because an absent arg yields `""`.

- [ ] **Step 4: Add the `sampleSms` arg to the route**

In `NavGraph.kt`, replace the rule-edit `composable` (currently `NavGraph.kt:74-85`):

```kotlin
        composable(
            route = "banks/{bankId}/rules/edit?ruleId={ruleId}&sampleSms={sampleSms}",
            arguments = listOf(
                navArgument("bankId") { type = NavType.LongType },
                navArgument("ruleId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("sampleSms") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            RuleEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
```

The existing navigations `"banks/$it/rules/edit"` and `"banks/$it/rules/edit?ruleId=$ruleId"` still work because `ruleId` and `sampleSms` both have defaults.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.banks.RuleEditorViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModel.kt app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt app/src/test/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModelTest.kt
git commit -m "feat(rule-editor): pre-fill sample SMS from navigation arg"
```

---

### Task 4: `UnparsedSmsViewModel`

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsViewModelTest.kt` (create)

**Interfaces:**
- Consumes: `ParseLogRepository.getAllLogs()` + `deleteFailed()` (Tasks 2), `BankRepository.getAllBanks()`, `SmsSyncUseCase.sync()` (existing), `detectBankForSender` (Task 1).
- Produces: `FailedSms`, `UnparsedFilter`, `UnparsedSmsUiState`, `UnparsedSmsViewModel` with public `parseLogs`, `failedLogs`, `banks`, `uiState` StateFlows and `setFilter`, `resync`, `consumeSyncMessage` methods. Consumed by Task 5.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsViewModelTest.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.unparsed

import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.ParseLogRepository
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import com.smsexpensetracker.domain.value.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class UnparsedSmsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val parseLogRepository = mockk<ParseLogRepository>()
    private val bankRepository = mockk<BankRepository>()
    private val smsSyncUseCase = mockk<SmsSyncUseCase>()

    private val hdfc = Bank(id = 1L, name = "HDFC Bank", smsSender = "HDFCBK")
    private val banks = listOf(hdfc)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun failedLog(
        body: String = "body",
        sender: String = "HDFCBK",
        at: LocalDateTime = LocalDateTime.of(2026, 8, 3, 10, 0),
        error: String? = "no match"
    ) = ParseLog(0L, body, sender, at, ParseStatus.FAILED, error)

    private fun viewModel() =
        UnparsedSmsViewModel(parseLogRepository, bankRepository, smsSyncUseCase)

    @Test
    fun `failedLogs dedupes by body with count and newest time`() = runTest(testDispatcher) {
        val t1 = LocalDateTime.of(2026, 8, 3, 10, 0)
        val t2 = LocalDateTime.of(2026, 8, 3, 11, 0)
        every { parseLogRepository.getAllLogs() } returns flowOf(
            listOf(failedLog(at = t1), failedLog(at = t2), failedLog(body = "other", at = t2))
        )
        every { bankRepository.getAllBanks() } returns flowOf(banks)
        val vm = viewModel()
        val job = launch { vm.failedLogs.collect {} }
        advanceUntilIdle()
        assertEquals(2, vm.failedLogs.value.size)
        val first = vm.failedLogs.value.first()
        assertEquals("body", first.smsBody)
        assertEquals(2, first.failCount)
        assertEquals(t2, first.lastParsedAt)
        job.cancel()
    }

    @Test
    fun `failedLogs excludes non-failed logs`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(
            listOf(
                failedLog(),
                ParseLog(1L, "ok", "HDFCBK", LocalDateTime.of(2026, 8, 3, 9, 0), ParseStatus.SUCCESS, null)
            )
        )
        every { bankRepository.getAllBanks() } returns flowOf(banks)
        val vm = viewModel()
        val job = launch { vm.failedLogs.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.failedLogs.value.size)
        assertEquals("body", vm.failedLogs.value.single().smsBody)
        job.cancel()
    }

    @Test
    fun `failedLogs detects bank from sender`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(listOf(failedLog()))
        every { bankRepository.getAllBanks() } returns flowOf(banks)
        val vm = viewModel()
        val job = launch { vm.failedLogs.collect {} }
        advanceUntilIdle()
        assertEquals(1L, vm.failedLogs.value.single().bankId)
        job.cancel()
    }

    @Test
    fun `failedLogs leaves bankId null for unknown sender`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(listOf(failedLog(sender = "UNKNOWN")))
        every { bankRepository.getAllBanks() } returns flowOf(banks)
        val vm = viewModel()
        val job = launch { vm.failedLogs.collect {} }
        advanceUntilIdle()
        assertNull(vm.failedLogs.value.single().bankId)
        job.cancel()
    }

    @Test
    fun `default filter is FAILED`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        val vm = viewModel()
        assertEquals(UnparsedFilter.FAILED, vm.uiState.value.filter)
    }

    @Test
    fun `setFilter updates the filter`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        val vm = viewModel()
        vm.setFilter(UnparsedFilter.ALL)
        assertEquals(UnparsedFilter.ALL, vm.uiState.value.filter)
    }

    @Test
    fun `resync deletes failed logs then runs sync and reports result`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        coEvery { parseLogRepository.deleteFailed() } returns Unit
        coEvery { smsSyncUseCase.sync() } returns SyncResult(scanned = 3, inserted = 1, unparsed = 2)
        val vm = viewModel()

        vm.resync()
        advanceUntilIdle()

        coVerifyOrder {
            parseLogRepository.deleteFailed()
            smsSyncUseCase.sync()
        }
        assertEquals("Scanned 3, added 1, unparsed 2", vm.uiState.value.syncMessage)
        assertFalse(vm.uiState.value.isSyncing)
    }

    @Test
    fun `resync with sync error reports failure message`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        coEvery { parseLogRepository.deleteFailed() } returns Unit
        coEvery { smsSyncUseCase.sync() } returns SyncResult(error = "boom")
        val vm = viewModel()

        vm.resync()
        advanceUntilIdle()

        assertEquals("Sync failed. Try again.", vm.uiState.value.syncMessage)
        assertFalse(vm.uiState.value.isSyncing)
    }

    @Test
    fun `resync with thrown exception reports failure message`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        coEvery { parseLogRepository.deleteFailed() } throws RuntimeException("db down")
        val vm = viewModel()

        vm.resync()
        advanceUntilIdle()

        assertEquals("Sync failed. Try again.", vm.uiState.value.syncMessage)
        assertFalse(vm.uiState.value.isSyncing)
    }

    @Test
    fun `resync is gated while a sync is in flight`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        coEvery { parseLogRepository.deleteFailed() } returns Unit
        coEvery { smsSyncUseCase.sync() } returns SyncResult()
        val vm = viewModel()

        vm.resync()
        vm.resync()
        advanceUntilIdle()

        coVerify(exactly = 1) { smsSyncUseCase.sync() }
    }

    @Test
    fun `consumeSyncMessage clears the message`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        coEvery { parseLogRepository.deleteFailed() } returns Unit
        coEvery { smsSyncUseCase.sync() } returns SyncResult()
        val vm = viewModel()

        vm.resync()
        advanceUntilIdle()
        vm.consumeSyncMessage()

        assertNull(vm.uiState.value.syncMessage)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.unparsed.UnparsedSmsViewModelTest"`
Expected: FAIL — `UnparsedSmsViewModel` does not exist (compile error).

- [ ] **Step 3: Implement the ViewModel**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsViewModel.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.unparsed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.parser.detectBankForSender
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.ParseLogRepository
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

enum class UnparsedFilter { FAILED, ALL }

data class FailedSms(
    val smsBody: String,
    val smsSender: String,
    val errorMessage: String?,
    val lastParsedAt: LocalDateTime,
    val failCount: Int,
    val bankId: Long?
)

data class UnparsedSmsUiState(
    val filter: UnparsedFilter = UnparsedFilter.FAILED,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null
)

@HiltViewModel
class UnparsedSmsViewModel @Inject constructor(
    private val parseLogRepository: ParseLogRepository,
    private val bankRepository: BankRepository,
    private val smsSyncUseCase: SmsSyncUseCase
) : ViewModel() {

    val parseLogs: StateFlow<List<ParseLog>> = parseLogRepository.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val banks: StateFlow<List<Bank>> = bankRepository.getAllBanks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val failedLogs: StateFlow<List<FailedSms>> =
        combine(parseLogs, banks) { logs, banks ->
            logs.filter { it.status == ParseStatus.FAILED }
                .groupBy { it.smsBody }
                .map { (_, group) ->
                    val newest = group.maxBy { it.parsedAt }
                    FailedSms(
                        smsBody = newest.smsBody,
                        smsSender = newest.smsSender,
                        errorMessage = newest.errorMessage,
                        lastParsedAt = newest.parsedAt,
                        failCount = group.size,
                        bankId = detectBankForSender(newest.smsSender, banks)
                    )
                }
                .sortedByDescending { it.lastParsedAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(UnparsedSmsUiState())
    val uiState: StateFlow<UnparsedSmsUiState> = _uiState.asStateFlow()

    fun setFilter(filter: UnparsedFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun resync() {
        if (_uiState.value.isSyncing) return
        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            try {
                parseLogRepository.deleteFailed()
                val result = smsSyncUseCase.sync()
                val message = if (result.error != null) {
                    "Sync failed. Try again."
                } else {
                    "Scanned ${result.scanned}, added ${result.inserted}, unparsed ${result.unparsed}"
                }
                _uiState.update { it.copy(isSyncing = false, syncMessage = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSyncing = false, syncMessage = "Sync failed. Try again.") }
            }
        }
    }

    fun consumeSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.unparsed.UnparsedSmsViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsViewModelTest.kt
git commit -m "feat(unparsed): add review view model with dedup and resync"
```

---

### Task 5: `UnparsedSmsScreen`

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsScreen.kt`

**Interfaces:**
- Consumes: `UnparsedSmsViewModel` (Task 4) via `hiltViewModel()`; `FailedSms`, `UnparsedFilter` from the ViewModel file.
- Produces: `@Composable fun UnparsedSmsScreen(onBack: () -> Unit, onFix: (Long, String) -> Unit, viewModel: UnparsedSmsViewModel = hiltViewModel())`. Consumed by Task 6. Compile-gated (Compose UI is manual-only in this project — no unit tests).

- [ ] **Step 1: Write the screen**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsScreen.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.unparsed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnparsedSmsScreen(
    onBack: () -> Unit,
    onFix: (Long, String) -> Unit,
    viewModel: UnparsedSmsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val failedLogs by viewModel.failedLogs.collectAsState()
    val parseLogs by viewModel.parseLogs.collectAsState()
    val banks by viewModel.banks.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.syncMessage) {
        state.syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSyncMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unparsed SMS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filter == UnparsedFilter.ALL,
                    onClick = { viewModel.setFilter(UnparsedFilter.ALL) },
                    label = { Text("All") },
                    shape = MaterialTheme.shapes.small
                )
                FilterChip(
                    selected = state.filter == UnparsedFilter.FAILED,
                    onClick = { viewModel.setFilter(UnparsedFilter.FAILED) },
                    label = { Text("Failed") },
                    shape = MaterialTheme.shapes.small
                )
            }

            Button(
                onClick = viewModel::resync,
                enabled = !state.isSyncing,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSyncing) "Syncing…" else "Re-sync now")
            }

            if (state.filter == UnparsedFilter.FAILED) {
                if (failedLogs.isEmpty()) {
                    Text(
                        text = "No unparsed SMS",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(failedLogs, key = { it.smsBody }) { failed ->
                            FailedSmsCard(failed = failed, banks = banks, onFix = onFix)
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(parseLogs, key = { it.id }) { log ->
                        ParseLogCard(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedSmsCard(
    failed: FailedSms,
    banks: List<Bank>,
    onFix: (Long, String) -> Unit
) {
    val bankName = banks.firstOrNull { it.id == failed.bankId }?.name
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = failed.smsSender,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                bankName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = "FAILED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = failed.smsBody,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Failed ${failed.failCount}x · ${failed.lastParsedAt}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            failed.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            val bankId = failed.bankId
            if (bankId != null) {
                Button(
                    onClick = { onFix(bankId, failed.smsBody) },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                ) {
                    Text("Fix")
                }
            } else {
                Text(
                    text = "No matching bank — add it in Banks & Rules first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ParseLogCard(log: ParseLog) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = log.smsSender,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = log.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (log.status == ParseStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                text = log.smsBody,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = log.parsedAt.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (The screen references the ViewModel from Task 4 and compiles standalone.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/unparsed/UnparsedSmsScreen.kt
git commit -m "feat(unparsed): add review screen UI"
```

---

### Task 6: Navigation route + Settings row

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `UnparsedSmsScreen` (Task 5), the `sampleSms` route arg (Task 3).
- Produces: route `"unparsed_sms"`; `SettingsScreen` gains `onNavigateToUnparsedSms: () -> Unit`. Compile-gated.

- [ ] **Step 1: Add the route and Settings wiring**

In `NavGraph.kt`:

1. Add import `import android.net.Uri` and `import com.smsexpensetracker.ui.screens.unparsed.UnparsedSmsScreen`.
2. Pass the new callback in the Settings composable (currently `NavGraph.kt:44-50`):

```kotlin
        composable(BottomNavItem.Settings.route) {
            SettingsScreen(
                onNavigateToCategories = { navController.navigate("categories") },
                onNavigateToBanks = { navController.navigate("banks") },
                onNavigateToLogs = { navController.navigate("logs") },
                onNavigateToUnparsedSms = { navController.navigate("unparsed_sms") }
            )
        }
```

3. Add the route after the `"logs"` composable:

```kotlin
        composable("unparsed_sms") {
            UnparsedSmsScreen(
                onBack = { navController.popBackStack() },
                onFix = { bankId, smsBody ->
                    navController.navigate("banks/$bankId/rules/edit?sampleSms=${Uri.encode(smsBody)}")
                }
            )
        }
```

- [ ] **Step 2: Add the Settings row**

In `SettingsScreen.kt`:

1. Add the parameter:

```kotlin
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToCategories: () -> Unit = {},
    onNavigateToBanks: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
    onNavigateToUnparsedSms: () -> Unit = {}
) {
```

2. Insert the row **directly above** the existing "Logs" row (`SettingsActionRow(icon = Icons.Filled.Description, label = "Logs", ...)` at `SettingsScreen.kt:225-229`):

```kotlin
        SettingsActionRow(
            icon = Icons.Filled.SmsFailed,
            label = "Unparsed SMS",
            onClick = onNavigateToUnparsedSms
        )
        SettingsActionRow(
            icon = Icons.Filled.Description,
            label = "Logs",
            onClick = onNavigateToLogs
        )
```

3. Add the icon import: `import androidx.compose.material.icons.filled.SmsFailed`.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt
git commit -m "feat(nav): add unparsed sms route and settings entry"
```

---

### Task 7: Docs + full verification

**Files:**
- Modify: `TESTING.md`
- Modify: `TODO.md`

**Interfaces:**
- Consumes: all prior tasks. Produces: updated docs + verified build.

- [ ] **Step 1: Add a manual QA section to TESTING.md**

Add a new section after §10 (Logs) — renumber the following sections if clean numbering matters (the file uses `## 11. Edge Cases & Known Behavior` etc.):

```markdown
## 11. Unparsed SMS Review

> Requires SMS permission and at least one SMS that fails to parse. To produce failures reliably: `scripts/push_test_sms.sh`, then temporarily disable a seeded rule (or sync before the 14-rule seed shipped) so some SMS don't match.

- [ ] Settings → **Unparsed SMS** (row sits directly above **Logs**). → Screen opens with the **Failed** filter selected.
- [ ] Each card shows the **full SMS body** (monospace, wrapped), sender, bank name (if the sender matches a bank), a red **FAILED** badge, "Failed N×", and the last attempt time.
- [ ] Duplicate bodies appear **once** with a count (e.g. same SMS pushed twice → "Failed 2x").
- [ ] Toggle the **All | Failed** chips. → Failed shows deduped cards; All shows raw parse-log rows (one per log entry).
- [ ] An SMS whose sender matches no bank → "Fix" is **disabled** and the hint "No matching bank — add it in Banks & Rules first." shows.
- [ ] Tap **Fix** on a failing SMS → the **Rule Editor** opens with the **Sample SMS** field pre-filled and the bank selected; write a matching template, **Test** → green "Matches", **Save**.
- [ ] Return to the review screen and tap **Re-sync now** → snackbar "Scanned X, added Y, unparsed Z"; the fixed SMS **no longer appears** (old FAILED rows were cleared first).
- [ ] Tap **Re-sync now** with SMS permission revoked → snackbar "Sync failed. Try again."; **Re-sync now** is disabled (spinner) while syncing.
```

Renumber the old `## 11. Edge Cases & Known Behavior` → `## 12.` (and its references). Update the summary table's "ViewModels" row to mention `UnparsedSmsViewModel`, and update the test total (310 → 310 + 6 DetectBankForSender + 1 ParseLogRepositoryImpl + 1 RuleEditor + 11 UnparsedSmsViewModel = **329**) in the table header and any inline references.

- [ ] **Step 2: Update TODO.md**

Under `### [x] 14. Settings Screen`, add a completed line:

```markdown
  - [x] **Unparsed SMS review screen** — Settings → "Unparsed SMS" lists failed SMS bodies (deduped, with count/bank/sender), "Fix" opens the Rule Editor pre-filled with the SMS body + detected bank, and "Re-sync now" clears stale FAILED parse logs before re-running sync
```

Also mark the Task 16 line `- [ ] Implement unparsed SMS list (tappable from completion banner)` with a note that the feature shipped via Settings (not the banner):

```markdown
- [~] Implement unparsed SMS list (tappable from completion banner) _(shipped as Settings → Unparsed SMS instead — the completion-banner entry point is deferred)_
```

- [ ] **Step 3: Full verification gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, **329 tests**, 0 failures, 0 errors.

- [ ] **Step 4: Commit**

```bash
git add TESTING.md TODO.md
git commit -m "docs: add unparsed SMS review to testing checklist"
```

---

## Self-Review Notes

**Spec coverage check (against `docs/superpowers/specs/2026-08-03-unparsed-sms-review-design.md`):**
- §4.1 navigation (Settings row above Logs, `unparsed_sms` route, `Uri.encode` onFix) → Task 6.
- §4.2 data layer (`deleteFailed`, single `getAllLogs` source, no schema change) → Task 2.
- §4.3 ViewModel (dedup by body → `FailedSms` w/ `failCount` + `lastParsedAt`, `UnparsedFilter.FAILED` default, in-memory filter, resync = delete-then-sync, snackbar message) → Task 4.
- §4.4 Rule Editor `sampleSms` prefill + nav arg → Task 3.
- §4.5 shared `detectBankForSender` + ParserViewModel delegate → Task 1.
- §4.6 screen (scaffold, filter chips All|Failed, Re-sync now w/ busy state, deduped cards, monospace body, FAILED badge, "Failed N×", Fix disabled + hint, empty state, snackbar) → Task 5.
- §5 behavior changes, §6 error handling (sync failure message, no-bank hint, Flow errors) → Tasks 4-6.
- §7 decisions (clear-on-resync, "All" future-proofing, `sampleSms` URI-encoding) → Tasks 2/4/6.
- §8 acceptance criteria → Tasks 4-6; final gate in Task 7.

**Type consistency:** `detectBankForSender(sender: String, banks: List<Bank>): Long?` defined in Task 1 and used in Task 4; `FailedSms` fields match between Task 4 (definition) and Task 5 (card UI); `deleteFailed()` signature identical across DAO/interface/impl (Task 2) and ViewModel call (Task 4); route string `"banks/$bankId/rules/edit?sampleSms=…"` built in Task 6 matches the route pattern added in Task 3. `onFix(bankId, smsBody)` non-null `bankId` matches the screen only enabling Fix when `bankId != null`.

**Placeholder scan:** Every step has concrete code or an explicit command; no "TBD"/"add validation"/"similar to Task N" placeholders.
