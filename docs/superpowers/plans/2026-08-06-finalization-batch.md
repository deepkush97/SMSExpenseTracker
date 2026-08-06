# Finalization Batch (F2/F3/F4/F7/F8) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship five TODO features in one batch: live sync progress (F2), sync controls in Settings (F3), auto-categorization on sync (F4), shared error components (F7), and Room DAO integration tests (F8).

**Architecture:** Purely additive on the existing single-module Compose + Hilt + Room app. F2 wires the already-emitting `SmsSyncUseCase.progress` StateFlow into the Transactions ViewModel/UI. F3 re-lands the deleted `SyncRange` value object and threads a range into `SmsSyncUseCase.sync()` + `SmsReader.readSms(dateRange)`, then adds a Settings sync section. F4 re-lands the two pruned entities, bumps DB to v7 with `MIGRATION_6_7`, adds DAOs/repos, and a pure `AutoCategoryEngine` applied inside `SmsSyncUseCase`. F7 adds three shared error composables. F8 adds in-memory DAO androidTests.

**Tech Stack:** Kotlin, Compose (Material3 1.5.0-alpha24), Hilt, Room + KSP, kotlinx-coroutines, MockK, `kotlinx-coroutines-test`, JUnit4.

## Global Constraints

- Package `com.smsexpensetracker`; min SDK 28 / target 36 / compile 37.
- All amounts are paisa `Long` — never `Double`/`BigDecimal`.
- DB schema exported to `app/schemas/` and committed; every `version` bump must add a migration + migration androidTest + committed schema JSON.
- Do NOT touch the `MainActivity`, `SmsIncomingReceiver`, Hilt modules, or `scripts/push_test_sms.sh`.
- Room `@Insert(onConflict = IGNORE)` dedup via `smsBodyHash` must keep working (existing behavior).
- Unit gate: `./gradlew testDebugUnitTest`. Instrumented gate: `./gradlew connectedDebugAndroidTest` (needs emulator; verify at minimum after F4 and F8).
- Do not add new dependencies. `room-testing` is already present.
- One commit per feature group (F2, F3, F4, F7, F8).

---

## Task 1: F2 — Wire sync progress into TransactionsViewModel

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/domain/value/SyncProgress.kt` (add a `percent` convenience)
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModelTest.kt`

**Interfaces:**
- Consumes: `SmsSyncUseCase.progress: StateFlow<SyncProgress>` (exists).
- Produces: `TransactionsUiState.syncProgress: SyncProgress?` (null when not syncing).

- [ ] **Step 1: Write the failing test** — append to `TransactionsViewModelTest`:

```kotlin
@Test
fun `sync progress from use case surfaces while syncing and clears after`() = runTest(testDispatcher) {
    coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
    every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
    every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
    every { smsSyncUseCase.progress } returns MutableStateFlow(SyncProgress(processed = 25, total = 100, unparsed = 2))
    val gate = CompletableDeferred<SyncResult>()
    coEvery { smsSyncUseCase.sync() } coAnswers { gate.await() }

    viewModel = TransactionsViewModel(
        getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase,
        demoDataPreferences, demoDataSeeder
    )
    backgroundScope.launch { viewModel.uiState.collect { } }
    advanceUntilIdle()

    viewModel.sync()
    advanceUntilIdle()

    val syncing = viewModel.uiState.value
    assertTrue(syncing.isSyncing)
    assertEquals(25, syncing.syncProgress?.processed)
    assertEquals(100, syncing.syncProgress?.total)
    assertEquals(2, syncing.syncProgress?.unparsed)

    gate.complete(SyncResult(scanned = 5, inserted = 2, unparsed = 1))
    advanceUntilIdle()

    val done = viewModel.uiState.value
    assertTrue(!done.isSyncing)
    assertNull(done.syncProgress)
}
```

Add imports `kotlinx.coroutines.CompletableDeferred` and `com.smsexpensetracker.domain.value.SyncProgress` to the test file.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.transactions.TransactionsViewModelTest"`
Expected: FAIL — `syncProgress` is not a property of `TransactionsUiState`.

- [ ] **Step 3: Add `percent` to SyncProgress**

Modify `SyncProgress.kt`:

```kotlin
data class SyncProgress(
    val processed: Int = 0,
    val total: Int = 0,
    val unparsed: Int = 0
) {
    val percent: Int get() = if (total > 0) (processed * 100) / total else 0
}
```

- [ ] **Step 4: Wire progress into the VM**

In `TransactionsViewModel.kt`:
1. Add `import com.smsexpensetracker.domain.value.SyncProgress`.
2. Add field `private val _syncProgress = MutableStateFlow<SyncProgress?>(null)`.
3. In `init`, add a collection block:
   ```kotlin
   viewModelScope.launch {
       smsSyncUseCase.progress.collect { _syncProgress.value = it }
   }
   ```
4. Add `syncProgress: SyncProgress? = null` to `TransactionsUiState`.
5. In the `combine(...) { array -> }`, add `_syncProgress` as the last flow argument and a 12th cast; pass `syncProgress = if (isSyncing) _syncProgress.value else null` into the `TransactionsUiState(...)` constructor.
6. In `sync()`, after `_isSyncing.value = false`, leave `_syncProgress.value` as-is (banner hides because `isSyncing` went false).

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.transactions.TransactionsViewModelTest"`
Expected: PASS (all existing tests still pass too).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/value/SyncProgress.kt app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModelTest.kt
git commit -m "feat: expose live sync progress in Transactions state"
```

---

## Task 2: F2 — Progress banner UI in TransactionsScreen

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt`

**Interfaces:**
- Consumes: `TransactionsUiState.syncProgress: SyncProgress?`, `isSyncing: Boolean`, `SyncProgress.percent: Int`.

- [ ] **Step 1: Add the banner composable and render it**

In `TransactionsScreen.kt`:
1. Add imports: `androidx.compose.material3.LinearProgressIndicator`, `com.smsexpensetracker.domain.value.SyncProgress`.
2. Inside the `LazyColumn` (after the `item(key = "search")` block, before `item(key = "filterSpacer")`), add:
   ```kotlin
   if (state.syncProgress != null) {
       item(key = "syncProgress") {
           Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
               Row(
                   modifier = Modifier.fillMaxWidth(),
                   horizontalArrangement = Arrangement.SpaceBetween
               ) {
                   Text(
                       text = "Scanning SMS…",
                       style = MaterialTheme.typography.bodySmall,
                       color = MaterialTheme.colorScheme.onSurfaceVariant
                   )
                   Text(
                       text = "${state.syncProgress.processed}/${state.syncProgress.total} (${state.syncProgress.percent}%)",
                       style = MaterialTheme.typography.bodySmall,
                       color = MaterialTheme.colorScheme.onSurfaceVariant
                   )
               }
               Spacer(Modifier.height(4.dp))
               LinearProgressIndicator(
                   progress = {
                       if (state.syncProgress.total > 0) {
                           state.syncProgress.processed.toFloat() / state.syncProgress.total
                       } else 0f
                   },
                   modifier = Modifier.fillMaxWidth()
               )
           }
       }
   }
   ```
   (If `LinearProgressIndicator(progress = { ... })` lambda overload is unavailable in this M3 alpha, use `progress = if (total > 0) processed.toFloat()/total else 0f` — verify at compile time.)
3. Keep the header sync button spinner as-is.

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt
git commit -m "feat: show live sync progress banner on Transactions screen"
```

---

## Task 3: F3 — Re-land SyncRange value object

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/domain/value/SyncRange.kt`
- Test: `app/src/test/java/com/smsexpensetracker/domain/value/SyncRangeTest.kt`

**Interfaces:**
- Produces: `data class SyncRange(val startTimestamp: Long, val endTimestamp: Long = System.currentTimeMillis())` with companion `LAST_1D`, `LAST_1W`, `LAST_2W`, `LAST_1M`, `LAST_3M`, `ALL`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smsexpensetracker.domain.value

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRangeTest {

    @Test
    fun `presets cover expected durations`() {
        val now = System.currentTimeMillis()
        assertTrue(now - SyncRange.LAST_1D.startTimestamp <= 86_400_000L + 1_000L)
        assertTrue(now - SyncRange.LAST_1W.startTimestamp <= 604_800_000L + 1_000L)
        assertTrue(now - SyncRange.LAST_2W.startTimestamp <= 1_209_600_000L + 1_000L)
        assertTrue(now - SyncRange.LAST_1M.startTimestamp <= 2_592_000_000L + 1_000L)
        assertTrue(now - SyncRange.LAST_3M.startTimestamp <= 7_776_000_000L + 1_000L)
        assertEquals(0L, SyncRange.ALL.startTimestamp)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.value.SyncRangeTest"`
Expected: FAIL — cannot resolve `SyncRange`.

- [ ] **Step 3: Create SyncRange**

```kotlin
package com.smsexpensetracker.domain.value

data class SyncRange(
    val startTimestamp: Long,
    val endTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private val now: Long get() = System.currentTimeMillis()
        val LAST_1D = SyncRange(now - 86_400_000L)
        val LAST_1W = SyncRange(now - 604_800_000L)
        val LAST_2W = SyncRange(now - 1_209_600_000L)
        val LAST_1M = SyncRange(now - 2_592_000_000L)
        val LAST_3M = SyncRange(now - 7_776_000_000L)
        val ALL = SyncRange(0L)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.value.SyncRangeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/value/SyncRange.kt app/src/test/java/com/smsexpensetracker/domain/value/SyncRangeTest.kt
git commit -m "feat: re-land SyncRange value object with duration presets"
```

---

## Task 4: F3 — Thread range through SmsSyncUseCase.sync()

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt`
- Test: `app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt`

**Interfaces:**
- Consumes: `SyncRange` (Task 3).
- Produces: `suspend fun SmsSyncUseCase.sync(range: SyncRange? = null): SyncResult` — passes `range?.let { it.startTimestamp to it.endTimestamp }` as `SmsReader.readSms(dateRange=...)`, with `SyncRange.ALL` and `null` both meaning full scan.

- [ ] **Step 1: Write the failing test** — append to `SmsSyncUseCaseTest`:

```kotlin
@Test
fun `sync with a range passes date range to readSms`() = runTest {
    coEvery { smsReader.readSms(dateRange = any()) } returns MutableStateFlow(emptyList())
    every { smsRuleRepository.getAllRules() } returns MutableStateFlow(emptyList())
    coEvery { syncMetaRepository.upsert(any()) } returns Unit

    useCase.sync(range = SyncRange.LAST_1W)

    coVerify {
        smsReader.readSms(
            dateRange = match { pair ->
                pair != null && pair.second > pair.first && pair.second <= System.currentTimeMillis()
            }
        )
    }
}

@Test
fun `sync with ALL range keeps full scan (null date range)`() = runTest {
    coEvery { smsReader.readSms(dateRange = null) } returns MutableStateFlow(emptyList())
    every { smsRuleRepository.getAllRules() } returns MutableStateFlow(emptyList())
    coEvery { syncMetaRepository.upsert(any()) } returns Unit

    useCase.sync(range = SyncRange.ALL)

    coVerify(exactly = 1) { smsReader.readSms(dateRange = null) }
}
```

Add `import com.smsexpensetracker.domain.value.SyncRange` to the test file.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.SmsSyncUseCaseTest"`
Expected: FAIL — `sync()` takes no range argument.

- [ ] **Step 3: Modify the use case**

In `SmsSyncUseCase.kt`:
1. Add `import com.smsexpensetracker.domain.value.SyncRange`.
2. Change signature to `suspend fun sync(range: SyncRange? = null): SyncResult`.
3. Replace the `readSms` call:
   ```kotlin
   val dateRange = range?.takeUnless { it == SyncRange.ALL }
       ?.let { it.startTimestamp to it.endTimestamp }
   val messages = smsReader.readSms(dateRange = dateRange).first()
   ```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.SmsSyncUseCaseTest"`
Expected: PASS (existing `coEvery { smsReader.readSms() }` stubs still match the default-null call).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt
git commit -m "feat: support sync date range in SmsSyncUseCase"
```

---

## Task 5: F3 — SettingsViewModel sync controls

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SmsSyncUseCase.sync(range)`, `SyncMetaRepository.get(): SyncMeta?`, `SyncMeta.lastSyncTimestamp: Long`, `SyncRange`.
- Produces: `SettingsUiState` fields `lastSyncTime: Long? = null`, `selectedSyncRange: SyncRange = SyncRange.ALL`, `isSyncing: Boolean = false`, `syncMessage: String? = null`; VM funcs `onSyncRangeChange(SyncRange)`, `resync()`, `consumeSyncMessage()`.

- [ ] **Step 1: Write the failing tests** — update `SettingsViewModelTest`:

1. Change the `viewModel()` helper to pass two extra mocked deps (add `private val smsSyncUseCase = mockk<SmsSyncUseCase>()` and `private val syncMetaRepository = mockk<SyncMetaRepository>()`; import `SmsSyncUseCase`, `SyncMetaRepository`, `SyncRange`):
   ```kotlin
   private fun viewModel() =
       SettingsViewModel(themePreferences, exportCsvUseCase, importCsvUseCase, demoDataSeeder, demoDataPreferences, smsSyncUseCase, syncMetaRepository)
   ```
2. Add tests:
   ```kotlin
   @Test
   fun `loads last sync time from repo`() = runTest(testDispatcher) {
       every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
       coEvery { syncMetaRepository.get() } returns SyncMeta(lastSyncTimestamp = 1750000000000L, lastSmsId = null)
       val viewModel = viewModel()
       val job = launch { viewModel.uiState.collect {} }
       advanceUntilIdle()
       assertEquals(1750000000000L, viewModel.uiState.value.lastSyncTime)
       job.cancel()
   }

   @Test
   fun `resync triggers use case with selected range`() = runTest(testDispatcher) {
       every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
       coEvery { syncMetaRepository.get() } returns SyncMeta(lastSyncTimestamp = 0L, lastSmsId = null)
       coEvery { smsSyncUseCase.sync(any()) } returns SyncResult(scanned = 3, inserted = 1, unparsed = 0)
       val viewModel = viewModel()
       val job = launch { viewModel.uiState.collect {} }
       advanceUntilIdle()

       viewModel.onSyncRangeChange(SyncRange.LAST_1W)
       viewModel.resync()
       advanceUntilIdle()

       coVerify { smsSyncUseCase.sync(SyncRange.LAST_1W) }
       assertTrue(!viewModel.uiState.value.isSyncing)
       assertTrue(viewModel.uiState.value.syncMessage!!.contains("Scanned 3"))
       job.cancel()
   }

   @Test
   fun `resync blocks while already syncing`() = runTest(testDispatcher) {
       every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
       coEvery { syncMetaRepository.get() } returns SyncMeta(lastSyncTimestamp = 0L, lastSmsId = null)
       coEvery { smsSyncUseCase.sync(any()) } returns SyncResult()
       val viewModel = viewModel()
       val job = launch { viewModel.uiState.collect {} }
       advanceUntilIdle()

       viewModel.resync()
       viewModel.resync()
       advanceUntilIdle()

       coVerify(exactly = 1) { smsSyncUseCase.sync(any()) }
       job.cancel()
   }
   ```
   Add imports: `com.smsexpensetracker.domain.model.SyncMeta`, `com.smsexpensetracker.domain.repository.SyncMetaRepository`, `com.smsexpensetracker.domain.usecase.SmsSyncUseCase`, `com.smsexpensetracker.domain.value.SyncRange`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.settings.SettingsViewModelTest"`
Expected: FAIL — `SettingsViewModel` constructor arity mismatch.

- [ ] **Step 3: Modify the ViewModel**

In `SettingsViewModel.kt`:
1. Add constructor params `private val smsSyncUseCase: SmsSyncUseCase` and `private val syncMetaRepository: SyncMetaRepository`; add imports for `SmsSyncUseCase`, `SyncMetaRepository`, `SyncRange`.
2. Add fields to `SettingsUiState`: `lastSyncTime: Long? = null`, `selectedSyncRange: SyncRange = SyncRange.ALL`, `isSyncing: Boolean = false`, `syncMessage: String? = null`.
3. In `init`, load last sync time:
   ```kotlin
   viewModelScope.launch {
       _uiState.update { it.copy(lastSyncTime = syncMetaRepository.get()?.lastSyncTimestamp) }
   }
   ```
4. Add functions:
   ```kotlin
   fun onSyncRangeChange(range: SyncRange) {
       _uiState.update { it.copy(selectedSyncRange = range) }
   }

   fun resync() {
       if (_uiState.value.isSyncing) return
       if (_uiState.value.demoDataLoaded) {
           _uiState.update { it.copy(showDemoBarrier = true) }
           return
       }
       _uiState.update { it.copy(isSyncing = true) }
       viewModelScope.launch {
           val result = smsSyncUseCase.sync(_uiState.value.selectedSyncRange)
           val meta = syncMetaRepository.get()
           _uiState.update {
               it.copy(
                   isSyncing = false,
                   lastSyncTime = meta?.lastSyncTimestamp ?: it.lastSyncTime,
                   syncMessage = result.error?.let { "Sync failed. Try again." }
                       ?: "Scanned ${result.scanned}, added ${result.inserted}, unparsed ${result.unparsed}"
               )
           }
       }
   }

   fun consumeSyncMessage() {
       _uiState.update { it.copy(syncMessage = null) }
   }
   ```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.settings.SettingsViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModelTest.kt
git commit -m "feat: add sync controls state to SettingsViewModel"
```

---

## Task 6: F3 — Settings sync section UI

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `SettingsUiState.lastSyncTime`, `selectedSyncRange`, `isSyncing`, `syncMessage`; `viewModel.onSyncRangeChange`, `viewModel.resync`, `viewModel.consumeSyncMessage`.

- [ ] **Step 1: Add sync section + snackbar wiring**

In `SettingsScreen.kt`:
1. Add imports: `androidx.compose.material3.FilterChip`, `androidx.compose.material3.LinearProgressIndicator`, `androidx.compose.material3.SnackbarResult`, `androidx.compose.material3.SnackbarDuration`, `com.smsexpensetracker.domain.value.SyncRange`, `java.time.Instant`, `java.time.ZoneId`.
2. Add a `LaunchedEffect(state.syncMessage)` block (mirror the csvMessage one) that shows a snackbar then calls `viewModel.consumeSyncMessage()`.
3. Insert a "Sync" section between the "Data" section rows and the "About" section:
   ```kotlin
   Spacer(modifier = Modifier.size(32.dp))

   Text(
       text = "Sync",
       style = MaterialTheme.typography.titleMedium,
       color = MaterialTheme.colorScheme.primary
   )

   Spacer(modifier = Modifier.size(8.dp))

   Text(
       text = state.lastSyncTime?.let { ts ->
           val dt = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDateTime()
           "Last sync: ${dt.dayOfMonth} ${dt.month.name.lowercase().take(3)} ${dt.hour}:${dt.minute.toString().padStart(2, '0')}"
       } ?: "Never synced",
       style = MaterialTheme.typography.bodyMedium,
       color = MaterialTheme.colorScheme.onSurfaceVariant
   )

   Spacer(modifier = Modifier.size(8.dp))

   val ranges = listOf(
       "1D" to SyncRange.LAST_1D,
       "1W" to SyncRange.LAST_1W,
       "2W" to SyncRange.LAST_2W,
       "1M" to SyncRange.LAST_1M,
       "3M" to SyncRange.LAST_3M,
       "All" to SyncRange.ALL
   )
   Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       ranges.forEach { (label, range) ->
           FilterChip(
               selected = state.selectedSyncRange == range,
               onClick = { viewModel.onSyncRangeChange(range) },
               label = { Text(label) }
           )
       }
   }

   Spacer(modifier = Modifier.size(8.dp))

   SettingsActionRow(
       icon = Icons.Filled.Refresh,
       label = if (state.isSyncing) "Syncing…" else "Re-sync now",
       onClick = { if (!state.isSyncing) viewModel.resync() }
   )

   if (state.isSyncing) {
       Spacer(modifier = Modifier.size(8.dp))
       LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
   }
   ```
   Add `import androidx.compose.material.icons.filled.Refresh` and `androidx.compose.foundation.layout.Arrangement`.
4. Ensure the `SettingsActionRow` onClick is not invoked while syncing.

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt
git commit -m "feat: add sync controls section to Settings screen"
```

---

## Task 7: F4 — Re-land entities, domain models, and DAOs

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/core/database/entity/UserCategoryRuleEntity.kt`
- Create: `app/src/main/java/com/smsexpensetracker/core/database/entity/TransactionLabelEntity.kt`
- Create: `app/src/main/java/com/smsexpensetracker/core/database/dao/UserCategoryRuleDao.kt`
- Create: `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionLabelDao.kt`
- Create: `app/src/main/java/com/smsexpensetracker/domain/model/UserCategoryRule.kt`
- Create: `app/src/main/java/com/smsexpensetracker/domain/model/TransactionLabel.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/SmsExpenseDatabase.kt` (register entities + DAO accessors)
- Modify: `app/src/main/java/com/smsexpensetracker/di/RepositoryModule.kt` (bind new repo — Task 8; entities/DAOs here only)

**Interfaces:**
- Produces: entities `UserCategoryRuleEntity(id=0, pattern: String, categoryId: Long)` (table `user_category_rules`, FK→categories CASCADE, index `categoryId`), `TransactionLabelEntity(id=0, transactionId: Long, label: String)` (table `transaction_labels`, FK→transactions CASCADE, index `transactionId`); DAOs `UserCategoryRuleDao`, `TransactionLabelDao`; domain `UserCategoryRule(id, pattern, categoryId)`, `TransactionLabel(id, transactionId, label)`.

- [ ] **Step 1: Re-land entity files** (exact historical definitions from git `HEAD~1`):

`UserCategoryRuleEntity.kt`:
```kotlin
package com.smsexpensetracker.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_category_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")]
)
data class UserCategoryRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pattern: String,
    val categoryId: Long
)
```

`TransactionLabelEntity.kt`:
```kotlin
package com.smsexpensetracker.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_labels",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transactionId")]
)
data class TransactionLabelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long,
    val label: String
)
```

- [ ] **Step 2: Create DAOs**

`UserCategoryRuleDao.kt`:
```kotlin
package com.smsexpensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.smsexpensetracker.core.database.entity.UserCategoryRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCategoryRuleDao {
    @Query("SELECT * FROM user_category_rules ORDER BY id")
    fun getAll(): Flow<List<UserCategoryRuleEntity>>

    @Insert
    suspend fun insert(rule: UserCategoryRuleEntity): Long

    @Delete
    suspend fun delete(rule: UserCategoryRuleEntity)

    @Query("DELETE FROM user_category_rules WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)
}
```

`TransactionLabelDao.kt`:
```kotlin
package com.smsexpensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.smsexpensetracker.core.database.entity.TransactionLabelEntity

@Dao
interface TransactionLabelDao {
    @Insert
    suspend fun insert(label: TransactionLabelEntity): Long

    @Query("SELECT * FROM transaction_labels WHERE transactionId = :transactionId")
    fun getAllForTransaction(transactionId: Long): kotlinx.coroutines.flow.Flow<List<TransactionLabelEntity>>

    @Query("DELETE FROM transaction_labels WHERE transactionId = :transactionId")
    suspend fun deleteForTransaction(transactionId: Long)
}
```

- [ ] **Step 3: Create domain models**

`UserCategoryRule.kt`:
```kotlin
package com.smsexpensetracker.domain.model

data class UserCategoryRule(
    val id: Long,
    val pattern: String,
    val categoryId: Long
)
```

`TransactionLabel.kt`:
```kotlin
package com.smsexpensetracker.domain.model

data class TransactionLabel(
    val id: Long,
    val transactionId: Long,
    val label: String
)
```

- [ ] **Step 4: Register in the database + DAO accessors**

In `SmsExpenseDatabase.kt`:
1. Add imports for the two entities.
2. Add `UserCategoryRuleEntity::class, TransactionLabelEntity::class` to `entities = [...]`.
3. Add accessors:
   ```kotlin
   abstract fun userCategoryRuleDao(): UserCategoryRuleDao
   abstract fun transactionLabelDao(): TransactionLabelDao
   ```
   (Add the `import com.smsexpensetracker.core.database.dao.UserCategoryRuleDao` / `TransactionLabelDao`.)
4. This changes the compiled schema — do NOT bump the version yet (Task 8 does).

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/database/entity/UserCategoryRuleEntity.kt app/src/main/java/com/smsexpensetracker/core/database/entity/TransactionLabelEntity.kt app/src/main/java/com/smsexpensetracker/core/database/dao/UserCategoryRuleDao.kt app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionLabelDao.kt app/src/main/java/com/smsexpensetracker/domain/model/UserCategoryRule.kt app/src/main/java/com/smsexpensetracker/domain/model/TransactionLabel.kt app/src/main/java/com/smsexpensetracker/core/database/SmsExpenseDatabase.kt
git commit -m "feat: re-land user category rule and transaction label entities and DAOs"
```

---

## Task 8: F4 — Repositories and DI bindings

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/domain/repository/TransactionLabelRepository.kt`
- Create: `app/src/main/java/com/smsexpensetracker/data/repository/TransactionLabelRepositoryImpl.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/domain/repository/CategoryRepository.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/CategoryRepositoryImpl.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/di/RepositoryModule.kt`

**Interfaces:**
- Produces:
  - `TransactionLabelRepository { suspend fun insert(label: TransactionLabel): Long }`
  - `CategoryRepository.getRules(): Flow<List<UserCategoryRule>>`, `insertRule(rule): Long`, `deleteRule(rule)`
  - `TransactionRepository.insertBatchReturningIds(transactions): List<Long>`
  - DI binds for `TransactionLabelRepositoryImpl`.

- [ ] **Step 1: Create TransactionLabelRepository + impl**

`TransactionLabelRepository.kt`:
```kotlin
package com.smsexpensetracker.domain.repository

import com.smsexpensetracker.domain.model.TransactionLabel

interface TransactionLabelRepository {
    suspend fun insert(label: TransactionLabel): Long
}
```

`TransactionLabelRepositoryImpl.kt`:
```kotlin
package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.TransactionLabelDao
import com.smsexpensetracker.core.database.entity.TransactionLabelEntity
import com.smsexpensetracker.domain.model.TransactionLabel
import com.smsexpensetracker.domain.repository.TransactionLabelRepository
import javax.inject.Inject

class TransactionLabelRepositoryImpl @Inject constructor(
    private val transactionLabelDao: TransactionLabelDao
) : TransactionLabelRepository {
    override suspend fun insert(label: TransactionLabel): Long =
        transactionLabelDao.insert(
            TransactionLabelEntity(id = label.id, transactionId = label.transactionId, label = label.label)
        )
}
```

- [ ] **Step 2: Extend CategoryRepository + impl**

Add to `CategoryRepository.kt`:
```kotlin
fun getRules(): Flow<List<UserCategoryRule>>
suspend fun insertRule(rule: UserCategoryRule): Long
suspend fun deleteRule(rule: UserCategoryRule)
```
(import `UserCategoryRule`)

In `CategoryRepositoryImpl.kt`:
1. Add `categoryRuleDao` via constructor param `private val userCategoryRuleDao: UserCategoryRuleDao` (Hilt provides DAOs).
2. Add:
   ```kotlin
   override fun getRules(): Flow<List<UserCategoryRule>> =
       userCategoryRuleDao.getAll().map { list -> list.map { it.toDomain() } }

   override suspend fun insertRule(rule: UserCategoryRule): Long =
       userCategoryRuleDao.insert(
           UserCategoryRuleEntity(id = rule.id, pattern = rule.pattern, categoryId = rule.categoryId)
       )

   override suspend fun deleteRule(rule: UserCategoryRule) {
       userCategoryRuleDao.delete(
           UserCategoryRuleEntity(id = rule.id, pattern = rule.pattern, categoryId = rule.categoryId)
       )
   }

   private fun UserCategoryRuleEntity.toDomain() = UserCategoryRule(id, pattern, categoryId)
   ```
   (imports `UserCategoryRule`, `UserCategoryRuleEntity`)

- [ ] **Step 3: Add insertBatchReturningIds**

`TransactionRepository.kt`: add
```kotlin
suspend fun insertBatchReturningIds(transactions: List<Transaction>): List<Long>
```

`TransactionRepositoryImpl.kt`: refactor so both methods share the hashing:
```kotlin
override suspend fun insertBatch(transactions: List<Transaction>): Int =
    insertBatchReturningIds(transactions).count { it > 0 }

override suspend fun insertBatchReturningIds(transactions: List<Transaction>): List<Long> {
    return transactionDao.insertBatchIgnore(
        transactions.map { tx ->
            tx.toEntity().copy(
                smsBodyHash = tx.rawSms.takeIf { it.isNotBlank() }?.let { raw ->
                    MessageDigest.getInstance("SHA-256")
                        .digest(raw.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                }
            )
        }
    ).toList()
}
```

- [ ] **Step 4: Add DI binding**

In `RepositoryModule.kt`, add imports + binding:
```kotlin
@Binds
@Singleton
abstract fun bindTransactionLabelRepository(
    impl: TransactionLabelRepositoryImpl
): TransactionLabelRepository
```

- [ ] **Step 5: Update CategoryRepositoryImpl tests**

Find `app/src/test/java/com/smsexpensetracker/data/repository/CategoryRepositoryImplTest.kt`. Add `userCategoryRuleDao = mockk()` to setup, pass to the constructor, and add tests for `getRules`/`insertRule`/`deleteRule` following the existing style:
```kotlin
@Test
fun `getRules maps dao rules to domain`() = runTest {
    every { userCategoryRuleDao.getAll() } returns flowOf(
        listOf(UserCategoryRuleEntity(id = 1L, pattern = "amazon", categoryId = 2L))
    )
    val rules = repo.getRules().first()
    assertEquals(1, rules.size)
    assertEquals("amazon", rules[0].pattern)
    assertEquals(2L, rules[0].categoryId)
}

@Test
fun `insertRule delegates to dao`() = runTest {
    coEvery { userCategoryRuleDao.insert(any()) } returns 9L
    val id = repo.insertRule(UserCategoryRule(id = 0L, pattern = "flipkart", categoryId = 3L))
    assertEquals(9L, id)
    coVerify { userCategoryRuleDao.insert(match { it.pattern == "flipkart" }) }
}
```
If the test file currently constructs `CategoryRepositoryImpl(categoryDao)` with one arg, update the call site. Read the existing file first.

- [ ] **Step 6: Run unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/repository/TransactionLabelRepository.kt app/src/main/java/com/smsexpensetracker/data/repository/TransactionLabelRepositoryImpl.kt app/src/main/java/com/smsexpensetracker/domain/repository/CategoryRepository.kt app/src/main/java/com/smsexpensetracker/data/repository/CategoryRepositoryImpl.kt app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt app/src/main/java/com/smsexpensetracker/di/RepositoryModule.kt app/src/test/java/com/smsexpensetracker/data/repository/CategoryRepositoryImplTest.kt
git commit -m "feat: add label and rule repositories, insertBatchReturningIds"
```

---

## Task 9: F4 — DB version 7 + MIGRATION_6_7 + migration test

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/SmsExpenseDatabase.kt`
- Modify: `app/src/androidTest/java/com/smsexpensetracker/core/database/MigrationTest.kt`

**Interfaces:**
- Produces: `SmsExpenseDatabase.version = 7`, `MIGRATION_6_7` (creates both tables + indexes), committed `7.json` (auto-generated), `MigrationTest.migrate6To7`.

- [ ] **Step 1: Bump version + add migration**

In `SmsExpenseDatabase.kt`:
1. `version = 6` → `version = 7`.
2. Add `MIGRATION_6_7` after `MIGRATION_5_6`:
   ```kotlin
   val MIGRATION_6_7 = object : Migration(6, 7) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL(
               "CREATE TABLE IF NOT EXISTS `user_category_rules` (" +
                   "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                   "`pattern` TEXT NOT NULL, " +
                   "`categoryId` INTEGER NOT NULL, " +
                   "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
           )
           db.execSQL(
               "CREATE INDEX IF NOT EXISTS `index_user_category_rules_categoryId` ON `user_category_rules` (`categoryId`)"
           )
           db.execSQL(
               "CREATE TABLE IF NOT EXISTS `transaction_labels` (" +
                   "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                   "`transactionId` INTEGER NOT NULL, " +
                   "`label` TEXT NOT NULL, " +
                   "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
           )
           db.execSQL(
               "CREATE INDEX IF NOT EXISTS `index_transaction_labels_transactionId` ON `transaction_labels` (`transactionId`)"
           )
       }
   }
   ```
3. Add `MIGRATION_6_7` to `.addMigrations(...)`.

- [ ] **Step 2: Write the migration test** — append to `MigrationTest`:

```kotlin
@Test
fun migrate6To7_createsRuleAndLabelTables() {
    helper.createDatabase("migration-test-v7", 6).use { db ->
        db.execSQL("INSERT INTO banks (id, name, smsSender) VALUES (1, 'HDFC Bank', 'HDFCBK')")
        db.execSQL("INSERT INTO categories (id, name, icon, color, isDefault) VALUES (1, 'Shopping', '', 0, 0)")
    }

    val db = helper.runMigrationsAndValidate("migration-test-v7", 7, true, SmsExpenseDatabase.MIGRATION_6_7)

    db.execSQL("PRAGMA foreign_keys = ON")
    db.execSQL("INSERT INTO user_category_rules (pattern, categoryId) VALUES ('amazon', 1)")

    db.query("SELECT pattern FROM user_category_rules").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("amazon", cursor.getString(0))
    }
    db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'transaction_labels'").use { cursor ->
        assertTrue(cursor.moveToFirst())
    }
    db.close()
}
```

- [ ] **Step 3: Generate schema + run instrumented migration test**

Run: `./gradlew connectedDebugAndroidTest --tests "com.smsexpensetracker.core.database.MigrationTest"`
Expected: PASS; a new `app/schemas/com.smsexpensetracker.core.database.SmsExpenseDatabase/7.json` is generated. Verify with:
```bash
python3 -c "import json; d=json.load(open('app/schemas/com.smsexpensetracker.core.database.SmsExpenseDatabase/7.json')); print([t['tableName'] for t in d['database']['entities']])"
```
Expected output includes `user_category_rules` and `transaction_labels`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/database/SmsExpenseDatabase.kt app/src/androidTest/java/com/smsexpensetracker/core/database/MigrationTest.kt app/schemas/com.smsexpensetracker.core.database.SmsExpenseDatabase/7.json
git commit -m "feat: bump DB to v7 with MIGRATION_6_7 for label/rule tables"
```

---

## Task 10: F4 — AutoCategoryEngine + tests

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/core/categorize/AutoCategoryEngine.kt`
- Test: `app/src/test/java/com/smsexpensetracker/core/categorize/AutoCategoryEngineTest.kt`

**Interfaces:**
- Produces: `object AutoCategoryEngine { fun matchCategory(description: String, rules: List<UserCategoryRule>): Long? }` — first case-insensitive substring match wins; returns `categoryId` or null.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smsexpensetracker.core.categorize

import com.smsexpensetracker.domain.model.UserCategoryRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoCategoryEngineTest {

    private val shopping = UserCategoryRule(id = 1L, pattern = "amazon", categoryId = 10L)
    private val food = UserCategoryRule(id = 2L, pattern = "zomato", categoryId = 11L)

    @Test
    fun `matches case-insensitive substring`() {
        assertEquals(10L, AutoCategoryEngine.matchCategory("PAYMENT VIA AMAZON IN", listOf(shopping)))
    }

    @Test
    fun `first matching rule wins`() {
        assertEquals(
            10L,
            AutoCategoryEngine.matchCategory("ZOMATO ORDER VIA AMAZON PAY", listOf(shopping, food))
        )
    }

    @Test
    fun `returns null when no rule matches`() {
        assertNull(AutoCategoryEngine.matchCategory("SWIGGY ORDER", listOf(shopping, food)))
    }

    @Test
    fun `returns null for empty rules`() {
        assertNull(AutoCategoryEngine.matchCategory("SWIGGY ORDER", emptyList()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.categorize.AutoCategoryEngineTest"`
Expected: FAIL — cannot resolve `AutoCategoryEngine`.

- [ ] **Step 3: Create the engine**

```kotlin
package com.smsexpensetracker.core.categorize

import com.smsexpensetracker.domain.model.UserCategoryRule

object AutoCategoryEngine {

    fun matchCategory(description: String, rules: List<UserCategoryRule>): Long? {
        val lower = description.lowercase()
        return rules.firstOrNull { lower.contains(it.pattern.lowercase()) }?.categoryId
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.categorize.AutoCategoryEngineTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/categorize/AutoCategoryEngine.kt app/src/test/java/com/smsexpensetracker/core/categorize/AutoCategoryEngineTest.kt
git commit -m "feat: add AutoCategoryEngine for rule-based categorization"
```

---

## Task 11: F4 — Wire auto-categorization into SmsSyncUseCase

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt`
- Test: `app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt`

**Interfaces:**
- Consumes: `AutoCategoryEngine.matchCategory`, `CategoryRepository.getRules()/getAllCategories()`, `TransactionRepository.insertBatchReturningIds`, `TransactionLabelRepository.insert`, domain `UserCategoryRule`, `TransactionLabel`.
- Produces: `sync()` and `handleIncomingSms()` assign `categoryId` when a rule matches and record a `TransactionLabel` per inserted matched transaction.

- [ ] **Step 1: Update existing tests for new constructor deps**

In `SmsSyncUseCaseTest`:
1. Add mock fields `categoryRepository = mockk<CategoryRepository>()` and `transactionLabelRepository = mockk<TransactionLabelRepository>()`; add imports.
2. In `setup()`, stub:
   ```kotlin
   every { categoryRepository.getRules() } returns flowOf(emptyList())
   every { categoryRepository.getAllCategories() } returns flowOf(emptyList())
   ```
3. Update the `SmsSyncUseCase(...)` constructor call to pass the two new params before `testDispatcher`:
   ```kotlin
   useCase = SmsSyncUseCase(
       smsReader, smsRuleRepository, transactionRepository, parseLogRepository, syncMetaRepository,
       bankRepository, demoDataPreferences, categoryRepository, transactionLabelRepository, testDispatcher
   )
   ```
4. Change every `coEvery { transactionRepository.insertBatch(any()) } returns 1` to `returns listOf(1L)` AND rename the method to `insertBatchReturningIds`. Same for `returns 0` → `returns emptyList()`, and all `coVerify { transactionRepository.insertBatch(...) }` → `insertBatchReturningIds(...)`. The `inserted` count math is unchanged because `listOf(1L).count { it > 0 } == 1`. Also update `handleIncomingSms` stubs similarly.
   - Mechanical bulk replace: `sed -i '' 's/transactionRepository\.insertBatch(/transactionRepository.insertBatchReturningIds(/g; s/coEvery { transactionRepository.insertBatchReturningIds(any()) } returns 1/coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(1L)/g; s/coEvery { transactionRepository.insertBatchReturningIds(any()) } returns 0/coEvery { transactionRepository.insertBatchReturningIds(any()) } returns emptyList()/g' app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt`
5. Run `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.SmsSyncUseCaseTest"` and fix any remaining compile issues (e.g., a `coVerify(exactly = 0) { transactionRepository.insertBatch(any()) }`).

- [ ] **Step 2: Write the new failing test** — append to `SmsSyncUseCaseTest`:

```kotlin
@Test
fun `sync applies category rule and records label`() = runTest {
    coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
    every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
    every { categoryRepository.getRules() } returns flowOf(
        listOf(UserCategoryRule(id = 1L, pattern = "acme", categoryId = 7L))
    )
    every { categoryRepository.getAllCategories() } returns flowOf(
        listOf(Category(id = 7L, name = "Shopping", icon = "", color = 0, isDefault = false))
    )
    coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(101L)
    coEvery { transactionLabelRepository.insert(any()) } returns 1L
    coEvery { parseLogRepository.insert(any()) } returns Unit
    coEvery { syncMetaRepository.upsert(any()) } returns Unit

    val result = useCase.sync()

    assertEquals(SyncResult(scanned = 1, inserted = 1, unparsed = 0), result)
    coVerify {
        transactionRepository.insertBatchReturningIds(
            match { list -> list.size == 1 && list[0].categoryId == 7L }
        )
    }
    coVerify {
        transactionLabelRepository.insert(
            match { label -> label.transactionId == 101L && label.label == "Shopping" }
        )
    }
}

@Test
fun `sync does not record label when no rule matches`() = runTest {
    coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
    every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
    every { categoryRepository.getRules() } returns flowOf(emptyList())
    every { categoryRepository.getAllCategories() } returns flowOf(emptyList())
    coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(101L)
    coEvery { parseLogRepository.insert(any()) } returns Unit
    coEvery { syncMetaRepository.upsert(any()) } returns Unit

    useCase.sync()

    coVerify(exactly = 0) { transactionLabelRepository.insert(any()) }
}
```
Add imports: `com.smsexpensetracker.domain.model.UserCategoryRule`, `com.smsexpensetracker.domain.model.Category`.

- [ ] **Step 3: Run to verify new tests fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.SmsSyncUseCaseTest"`
Expected: the two new tests FAIL (`categoryId` stays null / no label); existing tests PASS after Step 1.

- [ ] **Step 4: Modify SmsSyncUseCase**

In `SmsSyncUseCase.kt`:
1. Add constructor params `private val categoryRepository: CategoryRepository`, `private val transactionLabelRepository: TransactionLabelRepository` (before `ioDispatcher`). Add imports.
2. Change `sync()` body inside `withContext(ioDispatcher)`:
   ```kotlin
   val rules = smsRuleRepository.getAllRules().first().filter { it.isActive }
   val rulePairs = rules.map { it.bankId to it.pattern }
   val categoryRules = categoryRepository.getRules().first()
   val categories = categoryRepository.getAllCategories().first().associateBy { it.id }
   val dateRange = range?.takeUnless { it == SyncRange.ALL }?.let { it.startTimestamp to it.endTimestamp }
   val messages = smsReader.readSms(dateRange = dateRange).first()
   val total = messages.size

   var processed = 0
   var unparsed = 0
   var inserted = 0

   messages.chunked(100).forEach { chunk ->
       val transactions = mutableListOf<Transaction>()
       for (msg in chunk) {
           when (val result = classifySms(msg.body, msg.sender, msg.timestamp, rulePairs, categoryRules)) {
               is ClassifyResult.TransactionReady -> transactions += result.transaction
               ClassifyResult.ParseFailed -> unparsed++
               ClassifyResult.Skipped -> Unit
           }
           processed++
       }
       if (transactions.isNotEmpty()) {
           val ids = transactionRepository.insertBatchReturningIds(transactions)
           ids.forEachIndexed { index, id ->
               if (id > 0) {
                   transactions[index].categoryId?.let { catId ->
                       categories[catId]?.let { category ->
                           transactionLabelRepository.insert(
                               TransactionLabel(id = 0L, transactionId = id, label = category.name)
                           )
                       }
                   }
               }
           }
           inserted += ids.count { it > 0 }
       }
       _progress.value = SyncProgress(processed = processed, total = total, unparsed = unparsed)
   }
   ```
3. Update `classifySms` signature to accept `categoryRules: List<UserCategoryRule>` (add param before `writeParseLog`), and inside the `TransactionReady` branch set:
   ```kotlin
   categoryId = AutoCategoryEngine.matchCategory(parsed.description, categoryRules)
   ```
4. Update `handleIncomingSms`:
   ```kotlin
   val categoryRules = categoryRepository.getRules().first()
   val categories = categoryRepository.getAllCategories().first().associateBy { it.id }
   val rulePairs = ...
   ...
   when (val result = classifySms(body, sender, timestamp, rulePairs, categoryRules, writeParseLog = knownBank)) {
       is ClassifyResult.TransactionReady -> {
           val ids = transactionRepository.insertBatchReturningIds(listOf(result.transaction))
           ids.firstOrNull { it > 0 }?.let { id ->
               result.transaction.categoryId?.let { catId ->
                   categories[catId]?.let { category ->
                       transactionLabelRepository.insert(
                           TransactionLabel(id = 0L, transactionId = id, label = category.name)
                       )
                   }
               }
           }
           ids.count { it > 0 }
       }
       ClassifyResult.ParseFailed, ClassifyResult.Skipped -> 0
   }
   ```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.SmsSyncUseCaseTest"`
Expected: PASS.

- [ ] **Step 6: Full unit suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt
git commit -m "feat: auto-categorize transactions and record labels during sync"
```

---

## Task 12: F7 — Shared error components + banner wiring

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/components/ErrorBanner.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/components/ErrorSnackbar.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/components/ErrorDialog.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt`

**Interfaces:**
- Produces: `ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier)`, `ErrorSnackbar(message: String, snackbarHostState: SnackbarHostState, actionLabel: String? = null, onAction: () -> Unit = {})`, `ErrorDialog(title: String, message: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit)`.

- [ ] **Step 1: Create ErrorBanner.kt**

```kotlin
package com.smsexpensetracker.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
```

- [ ] **Step 2: Create ErrorSnackbar.kt**

```kotlin
package com.smsexpensetracker.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ErrorSnackbar(
    message: String,
    snackbarHostState: SnackbarHostState,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    LaunchedEffect(message) {
        if (message.isEmpty()) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) onAction()
    }
}
```

- [ ] **Step 3: Create ErrorDialog.kt**

```kotlin
package com.smsexpensetracker.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ErrorDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 4: Wire ErrorBanner into Transactions sync failure**

In `TransactionsScreen.kt`:
1. Add import `com.smsexpensetracker.ui.components.ErrorBanner`.
2. In the `LazyColumn`, before the `search` item, add:
   ```kotlin
   state.syncMessage?.takeIf { it.startsWith("Sync failed") }?.let { message ->
       item(key = "syncErrorBanner") {
           ErrorBanner(
               message = message,
               onDismiss = viewModel::consumeSyncMessage,
               modifier = Modifier.padding(vertical = 4.dp)
           )
       }
   }
   ```

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/components/ErrorBanner.kt app/src/main/java/com/smsexpensetracker/ui/components/ErrorSnackbar.kt app/src/main/java/com/smsexpensetracker/ui/components/ErrorDialog.kt app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt
git commit -m "feat: add shared error banner, snackbar, and dialog components"
```

---

## Task 13: F8 — Room DAO integration tests (androidTest)

**Files:**
- Create: `app/src/androidTest/java/com/smsexpensetracker/core/database/TransactionDaoTest.kt`
- Create: `app/src/androidTest/java/com/smsexpensetracker/core/database/BankDaoTest.kt`
- Create: `app/src/androidTest/java/com/smsexpensetracker/core/database/CategoryDaoTest.kt`
- Create: `app/src/androidTest/java/com/smsexpensetracker/core/database/SmsRuleDaoTest.kt`
- Create: `app/src/androidTest/java/com/smsexpensetracker/core/database/SyncMetaDaoTest.kt`
- Create: `app/src/androidTest/java/com/smsexpensetracker/core/database/UserCategoryRuleDaoTest.kt`
- Create: `app/src/androidTest/java/com/smsexpensetracker/core/database/TransactionLabelDaoTest.kt`

**Interfaces:**
- Consumes: `SmsExpenseDatabase`, all DAOs, entities, `TransactionType`, `ParseMethod`.

- [ ] **Step 1: Create TransactionDaoTest**

```kotlin
package com.smsexpensetracker.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.database.entity.BankEntity
import com.smsexpensetracker.core.database.entity.CategoryEntity
import com.smsexpensetracker.core.database.entity.ParseMethod
import com.smsexpensetracker.core.database.entity.TransactionEntity
import com.smsexpensetracker.core.database.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var db: SmsExpenseDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmsExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = db.transactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedBankAndCategory(): Pair<Long, Long> {
        val bankId = db.bankDao().insert(BankEntity(name = "HDFC Bank", smsSender = "HDFCBK"))
        val categoryId = db.categoryDao().insert(CategoryEntity(name = "Shopping", icon = "", color = 0))
        return bankId to categoryId
    }

    private fun tx(bankId: Long, categoryId: Long? = null, rawSms: String = "sms body", hash: String? = null) =
        TransactionEntity(
            bankId = bankId,
            amount = 1000L,
            type = TransactionType.DEBIT,
            description = "Test",
            transactionDate = LocalDateTime.now(),
            categoryId = categoryId,
            rawSms = rawSms,
            smsTimestamp = System.currentTimeMillis(),
            parseMethod = ParseMethod.SMS,
            smsBodyHash = hash
        )

    @Test
    fun `insert query update delete round trip`() = runTest {
        val (bankId, categoryId) = seedBankAndCategory()
        val id = transactionDao.insert(tx(bankId, categoryId))

        val found = transactionDao.getTransactionById(id)
        assertNotNull(found)
        assertEquals("Test", found!!.description)
        assertEquals(categoryId, found.categoryId)

        transactionDao.update(found.copy(description = "Updated"))
        assertEquals("Updated", transactionDao.getTransactionById(id)!!.description)

        transactionDao.delete(transactionDao.getTransactionById(id)!!)
        assertNull(transactionDao.getTransactionById(id))
    }

    @Test
    fun `insertBatchIgnore deduplicates by smsBodyHash`() = runTest {
        val (bankId, _) = seedBankAndCategory()
        val original = tx(bankId, rawSms = "same raw body", hash = "abc123")
        val dup = original.copy(id = 0L, smsBodyHash = "abc123")

        val ids = transactionDao.insertBatchIgnore(listOf(original, dup))

        assertEquals(2, ids.size)
        assertEquals(1, transactionDao.getAllTransactions().first().size)
        assertEquals(1L, ids.count { it > 0 })
    }

    @Test
    fun `update preserves smsBodyHash`() = runTest {
        val (bankId, _) = seedBankAndCategory()
        val id = transactionDao.insert(tx(bankId, rawSms = "body", hash = "keepme"))

        transactionDao.updateTransactionFields(
            id = id, bankId = bankId, amount = 9999L, type = TransactionType.CREDIT,
            description = "edited", transactionDate = LocalDateTime.now(), categoryId = null
        )

        assertEquals("keepme", transactionDao.getTransactionById(id)!!.smsBodyHash)
        assertEquals(9999L, transactionDao.getTransactionById(id)!!.amount)
    }

    @Test
    fun `insertBatchIgnore returns -1 for ignored duplicates`() = runTest {
        val (bankId, _) = seedBankAndCategory()
        val original = tx(bankId, rawSms = "raw", hash = "hash1")
        transactionDao.insertBatchIgnore(listOf(original))

        val second = transactionDao.insertBatchIgnore(listOf(original.copy(id = 0L)))

        assertEquals(1, second.size)
        assertEquals(-1L, second[0])
    }
}
```
Note: `db.bankDao()`/`db.categoryDao()` accessors already exist. If `Room.inMemoryDatabaseBuilder` build fails due to missing `room-testing` fixtures, ensure `allowMainThreadQueries()` is used; the existing `androidTestImplementation(libs.room.testing)` covers this.

- [ ] **Step 2: Create the remaining DAO tests** (each uses the exact `TransactionDaoTest` setup block: `Room.inMemoryDatabaseBuilder(context, SmsExpenseDatabase::class.java).allowMainThreadQueries().build()` in `@Before`, `db.close()` in `@After`).

`BankDaoTest.kt`:
```kotlin
@Test
fun `bank CRUD round trip`() = runTest {
    val id = db.bankDao().insert(BankEntity(name = "ICICI Bank", smsSender = "ICICIBK"))
    assertEquals(1, db.bankDao().getAllBanks().first().size)
    assertEquals("ICICI Bank", db.bankDao().getBankById(id)!!.name)

    db.bankDao().update(BankEntity(id = id, name = "ICICI", smsSender = "ICICIBK"))
    assertEquals("ICICI", db.bankDao().getBankById(id)!!.name)

    db.bankDao().delete(db.bankDao().getBankById(id)!!)
    assertEquals(0, db.bankDao().getAllBanks().first().size)
}

@Test
fun `bank lookup by sms sender`() = runTest {
    db.bankDao().insert(BankEntity(name = "HDFC Bank", smsSender = "HDFCBK"))
    assertEquals("HDFC Bank", db.bankDao().getBankBySmsSender("HDFCBK")!!.name)
}
```
(imports: `BankEntity`, `kotlinx.coroutines.flow.first`)

`CategoryDaoTest.kt`:
```kotlin
@Test
fun `category CRUD round trip`() = runTest {
    val id = db.categoryDao().insert(CategoryEntity(name = "Food", icon = "", color = 0, isDefault = true))
    val found = db.categoryDao().getAllCategoryById(id)!!
    assertTrue(found.isDefault)

    db.categoryDao().update(found.copy(name = "Dining"))
    assertEquals("Dining", db.categoryDao().getAllCategoryById(id)!!.name)

    db.categoryDao().delete(db.categoryDao().getAllCategoryById(id)!!)
    assertNull(db.categoryDao().getAllCategoryById(id))
}
```
(imports: `CategoryEntity`, `assertTrue`, `assertNull`)

`SmsRuleDaoTest.kt` (SmsRuleEntity requires a bank row):
```kotlin
@Test
fun `rule CRUD round trip`() = runTest {
    val bankId = db.bankDao().insert(BankEntity(name = "HDFC Bank", smsSender = "HDFCBK"))
    val rule = SmsRuleEntity(bankId = bankId, pattern = "Spent Rs\\.(.*)", description = "HDFC Debit")
    val id = db.smsRuleDao().insert(rule)

    assertEquals(1, db.smsRuleDao().getAllRules().first().size)
    assertEquals("HDFC Debit", db.smsRuleDao().getRuleById(id)!!.description)

    db.smsRuleDao().update(rule.copy(id = id, isActive = false))
    assertEquals(false, db.smsRuleDao().getRuleById(id)!!.isActive)

    db.smsRuleDao().delete(db.smsRuleDao().getRuleById(id)!!)
    assertEquals(0, db.smsRuleDao().getAllRules().first().size)
}
```
(imports: `BankEntity`, `SmsRuleEntity`, `kotlinx.coroutines.flow.first`)

`SyncMetaDaoTest.kt`:
```kotlin
@Test
fun `upsert then get returns same row`() = runTest {
    db.syncMetaDao().upsert(SyncMetaEntity(id = 1, lastSyncTimeStamp = 111L, lastSmsId = null))
    assertEquals(111L, db.syncMetaDao().get()!!.lastSyncTimeStamp)

    db.syncMetaDao().upsert(SyncMetaEntity(id = 1, lastSyncTimeStamp = 222L, lastSmsId = "99"))
    assertEquals(222L, db.syncMetaDao().get()!!.lastSyncTimeStamp)
    assertEquals("99", db.syncMetaDao().get()!!.lastSmsId)
}
```
(import: `SyncMetaEntity`)

`UserCategoryRuleDaoTest.kt` (FK→categories CASCADE):
```kotlin
@Test
fun `rule CRUD and cascade delete`() = runTest {
    val catId = db.categoryDao().insert(CategoryEntity(name = "Shopping", icon = "", color = 0))
    val rule = UserCategoryRuleEntity(pattern = "amazon", categoryId = catId)
    val id = db.userCategoryRuleDao().insert(rule)

    assertEquals(1, db.userCategoryRuleDao().getAll().first().size)
    assertEquals("amazon", db.userCategoryRuleDao().getAll().first()[0].pattern)

    db.userCategoryRuleDao().deleteByCategory(catId)
    assertEquals(0, db.userCategoryRuleDao().getAll().first().size)

    db.userCategoryRuleDao().insert(rule.copy(id = 0L))
    db.categoryDao().delete(db.categoryDao().getAllCategoryById(catId)!!)
    assertEquals(0, db.userCategoryRuleDao().getAll().first().size)
}
```
(imports: `CategoryEntity`, `UserCategoryRuleEntity`, `kotlinx.coroutines.flow.first`)

`TransactionLabelDaoTest.kt` (FK→transactions CASCADE; requires a bank + transaction):
```kotlin
@Test
fun `label CRUD and cascade delete`() = runTest {
    val bankId = db.bankDao().insert(BankEntity(name = "HDFC Bank", smsSender = "HDFCBK"))
    val txId = db.transactionDao().insert(
        TransactionEntity(
            bankId = bankId, amount = 1000L, type = TransactionType.DEBIT,
            description = "Test", transactionDate = LocalDateTime.now(), rawSms = "raw",
            smsTimestamp = System.currentTimeMillis()
        )
    )
    db.transactionLabelDao().insert(TransactionLabelEntity(transactionId = txId, label = "Shopping"))

    assertEquals(1, db.transactionLabelDao().getAllForTransaction(txId).first().size)
    assertEquals("Shopping", db.transactionLabelDao().getAllForTransaction(txId).first()[0].label)

    db.transactionLabelDao().deleteForTransaction(txId)
    assertEquals(0, db.transactionLabelDao().getAllForTransaction(txId).first().size)

    db.transactionLabelDao().insert(TransactionLabelEntity(transactionId = txId, label = "Shopping"))
    db.transactionDao().delete(db.transactionDao().getTransactionById(txId)!!)
    assertEquals(0, db.transactionLabelDao().getAllForTransaction(txId).first().size)
}
```
(imports: `BankEntity`, `TransactionEntity`, `TransactionType`, `TransactionLabelEntity`, `kotlinx.coroutines.flow.first`, `LocalDateTime`)

- [ ] **Step 3: Run instrumented tests**

Run: `./gradlew connectedDebugAndroidTest`
Expected: PASS — existing migration tests + all new DAO tests.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/smsexpensetracker/core/database/TransactionDaoTest.kt app/src/androidTest/java/com/smsexpensetracker/core/database/BankDaoTest.kt app/src/androidTest/java/com/smsexpensetracker/core/database/CategoryDaoTest.kt app/src/androidTest/java/com/smsexpensetracker/core/database/SmsRuleDaoTest.kt app/src/androidTest/java/com/smsexpensetracker/core/database/SyncMetaDaoTest.kt app/src/androidTest/java/com/smsexpensetracker/core/database/UserCategoryRuleDaoTest.kt app/src/androidTest/java/com/smsexpensetracker/core/database/TransactionLabelDaoTest.kt
git commit -m "test: add in-memory Room DAO integration tests"
```

---

## Task 14: Docs + final verification

**Files:**
- Modify: `TODO.md`
- Modify: `SOLUTION_DESIGN.md` (if needed)

- [ ] **Step 1: Update TODO.md**

- Mark **F2**, **F3**, **F4** complete in the Finalization Track.
- Update Task 14 `[-]` sync controls line → `[x]`.
- Update Task 16 sync progress lines → `[x]`.
- Note **F6**/**F9**/**F10** remain deferred (already `[ ]`).
- Update the stale count references if any test-count text appears (search for "green").

- [ ] **Step 2: Full verification**

Run: `./gradlew testDebugUnitTest` then `./gradlew connectedDebugAndroidTest`.
Expected: all green.

- [ ] **Step 3: Commit**

```bash
git add TODO.md SOLUTION_DESIGN.md
git commit -m "docs: mark F2/F3/F4/F7/F8 complete, note F6/F9/F10 deferred"
```

- [ ] **Step 4: Final check**

Run: `git log --oneline -15` — confirm one commit per feature group in order.
