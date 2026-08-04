# Demo-Data Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent demo transactions from ever being mixed with real data by blocking all real-data entry points (SMS sync, manual entry, CSV import) with a confirm dialog while a `demoDataLoaded` flag is set, plus a Settings "Delete demo data" row for proactive cleanup.

**Architecture:** A DataStore-backed `DemoDataPreferences.demoDataLoaded` flag (same pattern as `ThemePreferences`, no Room migration). `DemoDataSeeder.seedIfEmpty()` sets it when it inserts; a new `deleteDemoData()` wipes `transactions` and clears it. `SmsSyncUseCase.sync()` returns an error backstop when the flag is set. Each gated ViewModel observes the flag and shows a shared `DemoDataBarrierDialog` instead of running the action; confirming deletes demo data and requires the user to re-tap.

**Tech Stack:** Kotlin, Hilt, Room, Compose M3, DataStore Preferences, MockK + kotlinx-coroutines-test (JUnit 4).

## Global Constraints

- Package root: `com.smsexpensetracker`. Money is paisa `Long`. No code comments unless a task's code block includes them.
- Build gate (no lint/typecheck): `./gradlew assembleDebug` and `./gradlew cleanTestDebugUnitTest testDebugUnitTest`.
- Test baseline: 332 tests green (329 feature + 3 fix commits).
- `DemoDataPreferences` mirrors `ThemePreferences` exactly (same `DataStore<Preferences>`, provided by `SettingsModule`).
- Invariant that makes "delete demo" safe: `demoDataLoaded == true` ⇒ every transaction row is demo (seed only happens into empty DB; real entry is blocked while the flag is set). So `deleteDemoData()` = `DELETE FROM transactions` + clear flag.
- Dialog behavior (user-approved): **Delete demo data** deletes + dismisses; the blocked action is NOT auto-run — the user re-taps. **Cancel** dismisses only.
- Commit directly to `main`. NEVER stage the pre-existing dirty `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardViewModel.kt`, `opencode.json`, or untracked plan/spec docs.

---

### Task 1: `DemoDataPreferences`

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/core/settings/DemoDataPreferences.kt`
- Test: `app/src/test/java/com/smsexpensetracker/core/settings/DemoDataPreferencesTest.kt`

**Interfaces:**
- Produces: `DemoDataPreferences` with `val demoDataLoaded: Flow<Boolean>` (default `false`) and `suspend fun setDemoDataLoaded(loaded: Boolean)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smsexpensetracker.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DemoDataPreferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun createPreferences(): DemoDataPreferences {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
            produceFile = { tmp.newFile("test.preferences_pb") }
        )
        return DemoDataPreferences(dataStore)
    }

    @Test
    fun `defaults to false`() = runTest {
        assertFalse(createPreferences().demoDataLoaded.first())
    }

    @Test
    fun `round trips a written true value`() = runTest {
        val prefs = createPreferences()
        prefs.setDemoDataLoaded(true)
        assertTrue(prefs.demoDataLoaded.first())
    }

    @Test
    fun `round trips a written false value`() = runTest {
        val prefs = createPreferences()
        prefs.setDemoDataLoaded(true)
        prefs.setDemoDataLoaded(false)
        assertFalse(prefs.demoDataLoaded.first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.settings.DemoDataPreferencesTest" -v`
Expected: COMPILATION ERROR — `DemoDataPreferences` does not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.smsexpensetracker.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DemoDataPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val demoDataLoadedKey = booleanPreferencesKey("demo_data_loaded")

    val demoDataLoaded: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[demoDataLoadedKey] ?: false
    }

    suspend fun setDemoDataLoaded(loaded: Boolean) {
        dataStore.edit { prefs -> prefs[demoDataLoadedKey] = loaded }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.settings.DemoDataPreferencesTest" -v`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/settings/DemoDataPreferences.kt app/src/test/java/com/smsexpensetracker/core/settings/DemoDataPreferencesTest.kt
git commit -m "feat(demo): add DemoDataPreferences flag"
```

---

### Task 2: `TransactionDao.deleteAll` + repository

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt`
- Test: `app/src/test/java/com/smsexpensetracker/data/repository/TransactionRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `TransactionDao` (from Task 2 context; already injected in `TransactionRepositoryImpl`).
- Produces: `TransactionRepository.deleteAll(): suspend () -> Unit` and `TransactionDao.deleteAll()`.

- [ ] **Step 1: Write the failing test**

Open `app/src/test/java/com/smsexpensetracker/data/repository/TransactionRepositoryImplTest.kt`, read its setup (how `transactionDao` is mocked), then append a test verifying the delegate is called:

```kotlin
    @Test
    fun `deleteAll delegates to the dao`() = runTest {
        coEvery { transactionDao.deleteAll() } returns Unit
        val repo = TransactionRepositoryImpl(transactionDao)

        repo.deleteAll()

        coVerify(exactly = 1) { transactionDao.deleteAll() }
    }
```

Use the existing test class's imports and mock field names — match how the file is written (read it first). Add `coEvery`/`coVerify`/`runTest` imports if not already present.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.repository.TransactionRepositoryImplTest" -v`
Expected: COMPILATION ERROR — `deleteAll()` not defined on interface/DAO.

- [ ] **Step 3: Write minimal implementation**

Add to `TransactionDao`:

```kotlin
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
```

Add to `TransactionRepository` interface:

```kotlin
    suspend fun deleteAll()
```

Add to `TransactionRepositoryImpl`:

```kotlin
    override suspend fun deleteAll() {
        transactionDao.deleteAll()
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.repository.TransactionRepositoryImplTest" -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt app/src/test/java/com/smsexpensetracker/data/repository/TransactionRepositoryImplTest.kt
git commit -m "feat(transaction): add deleteAll to dao and repository"
```

---

### Task 3: `DemoDataSeeder` — set flag + `deleteDemoData()`

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/data/demo/DemoDataSeeder.kt`
- Test: `app/src/test/java/com/smsexpensetracker/data/demo/DemoDataSeederTest.kt`

**Interfaces:**
- Consumes: `DemoDataPreferences` (Task 1), `TransactionDao` (existing), `DemoTransactionGenerator` (existing).
- Produces: `DemoDataSeeder.deleteDemoData(): suspend () -> Unit`; `seedIfEmpty()` now returns `Int` AND sets the flag when it inserts.

- [ ] **Step 1: Update the existing test + write failing tests**

Rewrite `app/src/test/java/com/smsexpensetracker/data/demo/DemoDataSeederTest.kt`:

```kotlin
package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.settings.DemoDataPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoDataSeederTest {

    private val transactionDao = mockk<TransactionDao>()
    private val demoDataPreferences = mockk<DemoDataPreferences>()

    @Test
    fun `seeds when table is empty and sets demo flag`() = runTest {
        coEvery { transactionDao.count() } returns 0
        coEvery { transactionDao.insertAll(any()) } returns Unit
        coEvery { demoDataPreferences.setDemoDataLoaded(true) } returns Unit
        val inserted = DemoDataSeeder(transactionDao, demoDataPreferences).seedIfEmpty()
        coVerify { transactionDao.insertAll(any()) }
        coVerify(exactly = 1) { demoDataPreferences.setDemoDataLoaded(true) }
        assertEquals(DemoTransactionGenerator.generate().size, inserted)
    }

    @Test
    fun `skips when table has rows and does not set demo flag`() = runTest {
        coEvery { transactionDao.count() } returns 5
        val inserted = DemoDataSeeder(transactionDao, demoDataPreferences).seedIfEmpty()
        coVerify(exactly = 0) { transactionDao.insertAll(any()) }
        coVerify(exactly = 0) { demoDataPreferences.setDemoDataLoaded(true) }
        assertEquals(0, inserted)
    }

    @Test
    fun `deleteDemoData wipes transactions and clears the demo flag`() = runTest {
        coEvery { transactionDao.deleteAll() } returns Unit
        coEvery { demoDataPreferences.setDemoDataLoaded(false) } returns Unit

        DemoDataSeeder(transactionDao, demoDataPreferences).deleteDemoData()

        coVerify(exactly = 1) { transactionDao.deleteAll() }
        coVerify(exactly = 1) { demoDataPreferences.setDemoDataLoaded(false) }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.demo.DemoDataSeederTest" -v`
Expected: COMPILATION ERROR — `DemoDataSeeder` constructor doesn't take `DemoDataPreferences`; no `deleteDemoData()`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.settings.DemoDataPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoDataSeeder @Inject constructor(
    private val transactionDao: TransactionDao,
    private val demoDataPreferences: DemoDataPreferences
) {
    suspend fun seedIfEmpty(): Int {
        if (transactionDao.count() == 0) {
            val transactions = DemoTransactionGenerator.generate()
            transactionDao.insertAll(transactions)
            demoDataPreferences.setDemoDataLoaded(true)
            return transactions.size
        }
        return 0
    }

    suspend fun deleteDemoData() {
        transactionDao.deleteAll()
        demoDataPreferences.setDemoDataLoaded(false)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.demo.DemoDataSeederTest" -v`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/data/demo/DemoDataSeeder.kt app/src/test/java/com/smsexpensetracker/data/demo/DemoDataSeederTest.kt
git commit -m "feat(demo): set demo flag on seed and add deleteDemoData"
```

---

### Task 4: `SmsSyncUseCase` backstop guard

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt`
- Test: `app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt`

**Interfaces:**
- Consumes: `DemoDataPreferences` (Task 1).
- Produces: constructor parameter `demoDataPreferences: DemoDataPreferences`; `sync()` returns `SyncResult(error = "Delete demo data before syncing real SMS.")` when the flag is true.

- [ ] **Step 1: Update the test setup + write the failing test**

In `SmsSyncUseCaseTest`:
1. Add `private val demoDataPreferences = mockk<DemoDataPreferences>()` field + the import.
2. In `@Before setup()`, after `syncMetaRepository = mockk()`, add:
   ```kotlin
   demoDataPreferences = mockk()
   every { demoDataPreferences.demoDataLoaded } returns flowOf(false)
   ```
   Add `import kotlinx.coroutines.flow.flowOf`.
3. Change the `useCase = SmsSyncUseCase(...)` call to pass the new arg between `syncMetaRepository` and `testDispatcher`:
   ```kotlin
   useCase = SmsSyncUseCase(
       smsReader,
       smsRuleRepository,
       transactionRepository,
       parseLogRepository,
       syncMetaRepository,
       demoDataPreferences,
       testDispatcher
   )
   ```
4. Append the new test:

```kotlin
    @Test
    fun `sync returns backstop error when demo data is loaded`() = runTest {
        every { demoDataPreferences.demoDataLoaded } returns MutableStateFlow(true)

        val result = useCase.sync()

        assertEquals(
            SyncResult(error = "Delete demo data before syncing real SMS."),
            result
        )
        coVerify(exactly = 0) { smsReader.readSms() }
    }
```

- [ ] **Step 2: Run tests to verify the new test fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.SmsSyncUseCaseTest" -v`
Expected: The backstop test FAILS (sync runs and reads SMS, not returning the error); existing tests may fail to compile if constructor not updated — fix the constructor + stubs first.

- [ ] **Step 3: Write the implementation**

In `SmsSyncUseCase`, add `import com.smsexpensetracker.core.settings.DemoDataPreferences`, add the constructor param after `syncMetaRepository`, and add the guard at the top of `sync()` (before `isRunning = true`):

```kotlin
    suspend fun sync(): SyncResult {
        if (isRunning) return SyncResult()
        if (demoDataPreferences.demoDataLoaded.first()) {
            return SyncResult(error = "Delete demo data before syncing real SMS.")
        }
        isRunning = true
```

(`flow.first` is already imported in this file.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.SmsSyncUseCaseTest" -v`
Expected: PASS (all existing + new backstop test).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt
git commit -m "feat(sync): block sync while demo data is loaded"
```

---

### Task 5: Shared `DemoDataBarrierDialog` component

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/components/DemoDataBarrierDialog.kt`

**Interfaces:**
- Produces: `@Composable fun DemoDataBarrierDialog(onConfirmDelete: () -> Unit, onDismiss: () -> Unit)` — used by all four gated screens.

- [ ] **Step 1: Create the component** (no test — it's a stateless composable; the project has no Compose UI tests)

```kotlin
package com.smsexpensetracker.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DemoDataBarrierDialog(
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Demo data present") },
        text = {
            Text("Delete demo data before adding real data, so demo and real transactions don't mix.")
        },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) { Text("Delete demo data") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/components/DemoDataBarrierDialog.kt
git commit -m "feat(ui): add shared demo-data barrier dialog"
```

---

### Task 6: Gate Transactions sync

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModelTest.kt`

**Interfaces:**
- Consumes: `DemoDataPreferences` (Task 1), `DemoDataSeeder` (Task 3), `DemoDataBarrierDialog` (Task 5).
- Produces: `TransactionsViewModel.showDemoBarrier: StateFlow<Boolean>`; `sync()` opens the barrier instead of syncing when the flag is set; `confirmDeleteDemoData()` and `dismissDemoBarrier()`.

- [ ] **Step 1: Update the existing test + write the failing test**

In `TransactionsViewModelTest`:
1. Add imports: `DemoDataPreferences`, `DemoDataSeeder`.
2. Add fields `private val demoDataPreferences = mockk<DemoDataPreferences>()`, `private val demoDataSeeder = mockk<DemoDataSeeder>()`, and `private val demoDataLoadedFlow = MutableStateFlow(false)`. These mocks are created eagerly (the existing fields are `lateinit` + assigned in `setup()` — match that style: declare `private lateinit var demoDataPreferences: DemoDataPreferences` etc. and assign in `setup()`).
3. In `setup()`, after the existing `smsSyncUseCase = mockk()` line, add:
   ```kotlin
   demoDataPreferences = mockk()
   demoDataSeeder = mockk()
   every { demoDataPreferences.demoDataLoaded } returns demoDataLoadedFlow
   ```
   **This stub is REQUIRED** — the VM's new `init` block calls `demoDataPreferences.demoDataLoaded.collect { }`, and an unstubbed MockK property returns `null`, which would NPE in every existing test. Also add `import io.mockk.every` if not present.
4. Update ALL `TransactionsViewModel(...)` construction call sites (lines ~82, 102, 122, 138, 155, 169) to append `demoDataPreferences, demoDataSeeder` after `smsSyncUseCase`. Each test's setup already runs `advanceUntilIdle()` where needed; the `init` will collect `demoDataLoadedFlow`. Set the flag true in the new test.
4. Append:

```kotlin
    @Test
    fun `sync opens demo barrier instead of syncing when demo data present`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        viewModel = TransactionsViewModel(
            getTransactionsUseCase, bankRepository, categoryRepository,
            transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder
        )
        advanceUntilIdle()

        viewModel.sync()
        advanceUntilIdle()

        assertTrue(viewModel.showDemoBarrier.value)
        coVerify(exactly = 0) { smsSyncUseCase.sync() }
    }

    @Test
    fun `confirmDeleteDemoData deletes demo and closes barrier`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        viewModel = TransactionsViewModel(
            getTransactionsUseCase, bankRepository, categoryRepository,
            transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder
        )
        advanceUntilIdle()
        coEvery { demoDataSeeder.deleteDemoData() } returns Unit

        viewModel.confirmDeleteDemoData()
        advanceUntilIdle()

        coVerify(exactly = 1) { demoDataSeeder.deleteDemoData() }
        assertFalse(viewModel.showDemoBarrier.value)
    }
```

Read the existing test file first — match its `viewModel()` construction style and imports. The `demoDataLoadedFlow` must be wired so the VM's `init` collects it (see Step 3).

- [ ] **Step 2: Run tests to verify the new tests fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.transactions.TransactionsViewModelTest" -v`
Expected: COMPILATION ERROR until constructor updated; then the new tests FAIL because `showDemoBarrier` doesn't exist.

- [ ] **Step 3: Write the implementation**

In `TransactionsViewModel`:

Add imports:
```kotlin
import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.data.demo.DemoDataSeeder
import kotlinx.coroutines.flow.collect
```

Add constructor params (after `smsSyncUseCase`):
```kotlin
    private val demoDataPreferences: DemoDataPreferences,
    private val demoDataSeeder: DemoDataSeeder
```

Add state fields near the other private flows:
```kotlin
    private val _demoDataLoaded = MutableStateFlow(false)
    private val _showDemoBarrier = MutableStateFlow(false)
    val showDemoBarrier: StateFlow<Boolean> = _showDemoBarrier.asStateFlow()
```

Add an `init` block (there is no existing one in this VM — add one):
```kotlin
    init {
        viewModelScope.launch {
            demoDataPreferences.demoDataLoaded.collect { _demoDataLoaded.value = it }
        }
    }
```

Change `sync()`:
```kotlin
    fun sync() {
        if (_isSyncing.value) return
        if (_demoDataLoaded.value) {
            _showDemoBarrier.value = true
            return
        }
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
```

Add:
```kotlin
    fun dismissDemoBarrier() {
        _showDemoBarrier.value = false
    }

    fun confirmDeleteDemoData() {
        viewModelScope.launch {
            demoDataSeeder.deleteDemoData()
            _showDemoBarrier.value = false
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.transactions.TransactionsViewModelTest" -v`
Expected: PASS (existing + 2 new).

- [ ] **Step 5: Wire the screen**

In `TransactionsScreen.kt`, add `import com.smsexpensetracker.ui.components.DemoDataBarrierDialog`, collect the barrier near the top (after `val state by viewModel.uiState.collectAsState()`):
```kotlin
    val showDemoBarrier by viewModel.showDemoBarrier.collectAsState()
```
And render the dialog near the end of the composable (before the closing brace, alongside the existing `TransactionDetailSheet` block):
```kotlin
    if (showDemoBarrier) {
        DemoDataBarrierDialog(
            onConfirmDelete = viewModel::confirmDeleteDemoData,
            onDismiss = viewModel::dismissDemoBarrier
        )
    }
```

- [ ] **Step 6: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModelTest.kt
git commit -m "feat(transactions): gate sync behind demo-data barrier"
```

---

### Task 7: Gate Manual Entry save

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryViewModel.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryScreen.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryViewModelTest.kt`

**Interfaces:**
- Consumes: `DemoDataPreferences` (Task 1), `DemoDataSeeder` (Task 3), `DemoDataBarrierDialog` (Task 5).
- Produces: `ManualEntryUiState.showDemoBarrier: Boolean = false`; `save()` opens the barrier instead of inserting when the flag is set; `confirmDeleteDemoData()` / `dismissDemoBarrier()`.

- [ ] **Step 1: Update the existing test + write the failing test**

In `ManualEntryViewModelTest`:
1. Add fields + imports for `DemoDataPreferences` and `DemoDataSeeder`:
   ```kotlin
   private val demoDataPreferences = mockk<DemoDataPreferences>()
   private val demoDataSeeder = mockk<DemoDataSeeder>()
   private val demoDataLoadedFlow = MutableStateFlow(false)
   ```
   Add `every { demoDataPreferences.demoDataLoaded } returns demoDataLoadedFlow` to `setup()`.
2. Update the factory `createViewModel()` to:
   ```kotlin
   private fun createViewModel(): ManualEntryViewModel =
       ManualEntryViewModel(
           bankRepository, categoryRepository, transactionRepository,
           demoDataPreferences, demoDataSeeder
       )
   ```
3. Append:

```kotlin
    @Test
    fun `save opens demo barrier instead of inserting when demo data present`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100.00")
        vm.onPayeeChange("Cafe")
        vm.onBankChange(1L)

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showDemoBarrier)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `confirmDeleteDemoData deletes demo and closes barrier`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        coEvery { demoDataSeeder.deleteDemoData() } returns Unit
        val vm = createViewModel()
        advanceUntilIdle()

        vm.confirmDeleteDemoData()
        advanceUntilIdle()

        coVerify(exactly = 1) { demoDataSeeder.deleteDemoData() }
        assertFalse(vm.uiState.value.showDemoBarrier)
    }
```

- [ ] **Step 2: Run tests to verify the new tests fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.manualentry.ManualEntryViewModelTest" -v`
Expected: COMPILATION ERROR until the constructor is updated; then new tests FAIL.

- [ ] **Step 3: Write the implementation**

In `ManualEntryViewModel`:
- Add imports for `DemoDataPreferences`, `DemoDataSeeder`, `kotlinx.coroutines.flow.collect`.
- Add constructor params after `transactionRepository`:
  ```kotlin
      private val demoDataPreferences: DemoDataPreferences,
      private val demoDataSeeder: DemoDataSeeder
  ```
- Add fields to `ManualEntryUiState`:
  ```kotlin
      val demoDataLoaded: Boolean = false,
      val showDemoBarrier: Boolean = false
  ```
- In the existing `init` block, append a second launch:
  ```kotlin
      viewModelScope.launch {
          demoDataPreferences.demoDataLoaded.collect { loaded ->
              _uiState.update { it.copy(demoDataLoaded = loaded) }
          }
      }
  ```
- At the top of `save()`, after `if (current.isSaving) return`:
  ```kotlin
          if (current.demoDataLoaded) {
              _uiState.update { it.copy(showDemoBarrier = true) }
              return
          }
  ```
- Add:
  ```kotlin
  fun dismissDemoBarrier() = _uiState.update { it.copy(showDemoBarrier = false) }

  fun confirmDeleteDemoData() {
      viewModelScope.launch {
          demoDataSeeder.deleteDemoData()
          _uiState.update { it.copy(showDemoBarrier = false, demoDataLoaded = false) }
      }
  }
  ```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.manualentry.ManualEntryViewModelTest" -v`
Expected: PASS.

- [ ] **Step 5: Wire the screen**

In `ManualEntryScreen.kt`, add `import com.smsexpensetracker.ui.components.DemoDataBarrierDialog`, and near the end (after the `if (showDatePicker)` block):
```kotlin
    if (state.showDemoBarrier) {
        DemoDataBarrierDialog(
            onConfirmDelete = viewModel::confirmDeleteDemoData,
            onDismiss = viewModel::dismissDemoBarrier
        )
    }
```

- [ ] **Step 6: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryViewModel.kt app/src/main/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryScreen.kt app/src/test/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryViewModelTest.kt
git commit -m "feat(manual-entry): gate save behind demo-data barrier"
```

---

### Task 8: Gate Parser add-as-transaction

> Note: the spec listed "Parser screen sync", but `ParserViewModel` has no `sync()` — its real-data entry point is `addAsTransaction()` ("Add as Transaction"). This is the correct gate target.

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserViewModel.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserScreen.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/parser/ParserViewModelTest.kt`

**Interfaces:**
- Consumes: `DemoDataPreferences` (Task 1), `DemoDataSeeder` (Task 3), `DemoDataBarrierDialog` (Task 5).
- Produces: `ParserUiState.showDemoBarrier: Boolean = false`; `addAsTransaction()` opens the barrier instead of inserting when the flag is set; `confirmDeleteDemoData()` / `dismissDemoBarrier()`.

- [ ] **Step 1: Update the existing test + write the failing test**

In `ParserViewModelTest`:
1. Add fields + imports for `DemoDataPreferences` and `DemoDataSeeder`:
   ```kotlin
   private val demoDataPreferences = mockk<DemoDataPreferences>()
   private val demoDataSeeder = mockk<DemoDataSeeder>()
   private val demoDataLoadedFlow = MutableStateFlow(false)
   ```
   Add `every { demoDataPreferences.demoDataLoaded } returns demoDataLoadedFlow` in `setup()`.
2. Update `createViewModel()` (line ~78-86) to pass the new args and add the stubs (the existing method already stubs banks/rules/insert — extend it):
   ```kotlin
   private fun createViewModel(
       banks: List<Bank> = listOf(hdfcBank, iciciBank),
       rules: List<SmsRule> = listOf(hdfcDebitRule, hdfcCreditRule, iciciDebitRule)
   ): ParserViewModel {
       every { bankRepository.getAllBanks() } returns MutableStateFlow(banks)
       every { smsRuleRepository.getAllRules() } returns MutableStateFlow(rules)
       coEvery { transactionRepository.insert(any()) } returns 1L
       return ParserViewModel(
           bankRepository, smsRuleRepository, transactionRepository,
           demoDataPreferences, demoDataSeeder
       )
   }
   ```
3. Append a test modeled on the existing `addAsTransaction inserts parsed transaction` test (lines ~193-222), which inlines the SMS body. Use the exact same body string `"Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161"`:

```kotlin
    @Test
    fun `addAsTransaction opens demo barrier instead of inserting when demo data present`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onSmsChange(
            "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161"
        )
        viewModel.onSenderChange("AD-HDFCBK-S")
        advanceUntilIdle()
        viewModel.parse()
        advanceUntilIdle()

        viewModel.addAsTransaction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showDemoBarrier)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }
```

This mirrors the existing test exactly (same `viewModel` field, same `backgroundScope` collect, same inline body) — read lines 193-222 and copy its structure verbatim.

- [ ] **Step 2: Run tests to verify the new test fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.parser.ParserViewModelTest" -v`
Expected: COMPILATION ERROR until constructor updated; then new test FAILS.

- [ ] **Step 3: Write the implementation**

In `ParserViewModel`:
- Add imports for `DemoDataPreferences`, `DemoDataSeeder`, `kotlinx.coroutines.flow.collect`.
- Add constructor params after `transactionRepository`:
  ```kotlin
      private val demoDataPreferences: DemoDataPreferences,
      private val demoDataSeeder: DemoDataSeeder
  ```
- Add to `ParserUiState`:
  ```kotlin
      val demoDataLoaded: Boolean = false,
      val showDemoBarrier: Boolean = false
  ```
- In the existing `init` block, append a second launch:
  ```kotlin
      viewModelScope.launch {
          demoDataPreferences.demoDataLoaded.collect { loaded ->
              _uiState.update { it.copy(demoDataLoaded = loaded) }
          }
      }
  ```
- At the top of `addAsTransaction()`, after the guard line:
  ```kotlin
        if (current.isSaving || result == null || bankId == null || result.amount <= 0) return
  ```
  add:
  ```kotlin
        if (current.demoDataLoaded) {
            _uiState.update { it.copy(showDemoBarrier = true) }
            return
        }
  ```
- Add:
  ```kotlin
  fun dismissDemoBarrier() = _uiState.update { it.copy(showDemoBarrier = false) }

  fun confirmDeleteDemoData() {
      viewModelScope.launch {
          demoDataSeeder.deleteDemoData()
          _uiState.update { it.copy(showDemoBarrier = false, demoDataLoaded = false) }
      }
  }
  ```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.parser.ParserViewModelTest" -v`
Expected: PASS.

- [ ] **Step 5: Wire the screen**

In `ParserScreen.kt`, add `import com.smsexpensetracker.ui.components.DemoDataBarrierDialog`, and near the end of the composable (after the `Scaffold` block closes, inside the outer function body):
```kotlin
    if (state.showDemoBarrier) {
        DemoDataBarrierDialog(
            onConfirmDelete = viewModel::confirmDeleteDemoData,
            onDismiss = viewModel::dismissDemoBarrier
        )
    }
```

- [ ] **Step 6: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserViewModel.kt app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserScreen.kt app/src/test/java/com/smsexpensetracker/ui/screens/parser/ParserViewModelTest.kt
git commit -m "feat(parser): gate add-as-transaction behind demo-data barrier"
```

---

### Task 9: Gate CSV import + Settings delete-demo row

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `DemoDataPreferences` (Task 1), `DemoDataSeeder` (Task 3, already injected), `DemoDataBarrierDialog` (Task 5).
- Produces: `SettingsUiState.demoDataLoaded: Boolean`, `SettingsUiState.showDemoBarrier: Boolean`; `importCsv()` opens the barrier when the flag is set; `requestDeleteDemo()` opens the same barrier from the Settings row; `confirmDeleteDemoData()` deletes + closes + sets `demoMessage`; `dismissDemoBarrier()`.

- [ ] **Step 1: Update the existing test + write the failing tests**

In `SettingsViewModelTest`:
1. Add `DemoDataPreferences` field + import:
   ```kotlin
   private val demoDataPreferences = mockk<DemoDataPreferences>()
   private val demoDataLoadedFlow = MutableStateFlow(false)
   ```
   In `setup()` add `every { demoDataPreferences.demoDataLoaded } returns demoDataLoadedFlow`.
2. Update the factory (line 52) to:
   ```kotlin
   private fun viewModel() =
       SettingsViewModel(themePreferences, exportCsvUseCase, importCsvUseCase, demoDataSeeder, demoDataPreferences)
   ```
3. Append:

```kotlin
    @Test
    fun `importCsv opens demo barrier instead of importing when demo data present`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.importCsv(mockk<Uri>())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showDemoBarrier)
        coVerify(exactly = 0) { importCsvUseCase(any()) }
    }

    @Test
    fun `requestDeleteDemo opens the barrier`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.requestDeleteDemo()

        assertTrue(viewModel.uiState.value.showDemoBarrier)
    }

    @Test
    fun `confirmDeleteDemoData deletes demo, closes barrier, sets message`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        coEvery { demoDataSeeder.deleteDemoData() } returns Unit
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.confirmDeleteDemoData()
        advanceUntilIdle()

        coVerify(exactly = 1) { demoDataSeeder.deleteDemoData() }
        assertFalse(viewModel.uiState.value.showDemoBarrier)
        assertEquals("Demo data deleted", viewModel.uiState.value.demoMessage)
    }
```

- [ ] **Step 2: Run tests to verify the new tests fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.settings.SettingsViewModelTest" -v`
Expected: COMPILATION ERROR until constructor updated; then new tests FAIL.

- [ ] **Step 3: Write the implementation**

In `SettingsViewModel`:
- Add imports for `DemoDataPreferences` and `kotlinx.coroutines.flow.collect` (already imported).
- Add constructor param after `demoDataSeeder`:
  ```kotlin
      private val demoDataPreferences: DemoDataPreferences
  ```
- Add to `SettingsUiState`:
  ```kotlin
      val demoDataLoaded: Boolean = false,
      val showDemoBarrier: Boolean = false
  ```
- In the existing `init` block, append a second launch:
  ```kotlin
      viewModelScope.launch {
          demoDataPreferences.demoDataLoaded.collect { loaded ->
              _uiState.update { it.copy(demoDataLoaded = loaded) }
          }
      }
  ```
- At the top of `importCsv()`, after `if (_uiState.value.isCsvBusy) return`:
  ```kotlin
          if (_uiState.value.demoDataLoaded) {
              _uiState.update { it.copy(showDemoBarrier = true) }
              return
          }
  ```
- Add:
  ```kotlin
  fun requestDeleteDemo() = _uiState.update { it.copy(showDemoBarrier = true) }

  fun dismissDemoBarrier() = _uiState.update { it.copy(showDemoBarrier = false) }

  fun confirmDeleteDemoData() {
      viewModelScope.launch {
          demoDataSeeder.deleteDemoData()
          _uiState.update {
              it.copy(showDemoBarrier = false, demoDataLoaded = false, demoMessage = "Demo data deleted")
          }
      }
  }
  ```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.settings.SettingsViewModelTest" -v`
Expected: PASS.

- [ ] **Step 5: Wire the screen**

In `SettingsScreen.kt`:
1. Add `import com.smsexpensetracker.ui.components.DemoDataBarrierDialog` and `import androidx.compose.material.icons.filled.DeleteForever`.
2. Add a "Delete demo data" row directly below the "Load demo data" `SettingsActionRow` (only when `state.demoDataLoaded`):
   ```kotlin
        if (state.demoDataLoaded) {
            SettingsActionRow(
                icon = Icons.Filled.DeleteForever,
                label = "Delete demo data",
                onClick = { viewModel.requestDeleteDemo() }
            )
        }
   ```
3. At the end of the composable (inside the outer `Box`):
   ```kotlin
       if (state.showDemoBarrier) {
           DemoDataBarrierDialog(
               onConfirmDelete = viewModel::confirmDeleteDemoData,
               onDismiss = viewModel::dismissDemoBarrier
           )
       }
   ```

- [ ] **Step 6: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModel.kt app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt app/src/test/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): gate CSV import and add delete-demo-data row"
```

---

### Task 10: Full gate + docs

**Files:**
- Modify: `TESTING.md`
- Modify: `TODO.md`

**Interfaces:**
- Consumes: everything from Tasks 1–9.

- [ ] **Step 1: Run the full gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: ALL PASS, BUILD SUCCESSFUL. Baseline 332 + new tests: +3 (Task 1) +1 (Task 2) +1 (Task 3) +1 (Task 4) +2 (Task 6) +2 (Task 7) +1 (Task 8) +3 (Task 9) = **346**. If your actual count differs, record the real number.

- [ ] **Step 2: Update TESTING.md**

Add a new manual-QA section for the demo-data gate. Read the file first — it has numbered sections (currently through §12) with a "Not covered (manual-only)" list and a summary table. Append the newest section after §12 (renumber only if the file uses hard numbers AND the existing sections are contiguous — otherwise add as §13):

```markdown
## 13. Demo-Data Gate

A tap-through test plan. Every item is **Action → Expected result**.

- [ ] Settings → **Load demo data** with an empty DB → "Loaded N demo transactions".
- [ ] Transactions screen → tap **Sync** (or the refresh icon) → **Demo data present** dialog appears; **Cancel** dismisses; no sync runs.
- [ ] In the same dialog, tap **Delete demo data** → dialog closes; re-tap **Sync** → sync runs normally (permission prompt if not granted).
- [ ] Manual entry → fill valid amount + payee → **Save** → **Demo data present** dialog appears (transaction NOT saved). Delete demo data, re-tap Save → "Transaction saved".
- [ ] Settings → **Import CSV** → **Demo data present** dialog appears (import does NOT run). After deleting demo data, Import CSV works.
- [ ] Parser screen → paste a parseable bank SMS, **Test Parse** → **Add as Transaction** → **Demo data present** dialog appears; no transaction inserted.
- [ ] Settings → **Delete demo data** row is visible only while demo data is loaded. Tap it → **Demo data present** dialog → Delete → snackbar "Demo data deleted"; row disappears.
- [ ] **Delete demo data** wipes ALL transactions (demo rows are the only rows that can exist). Dashboard shows empty state.
- [ ] Real data path is never blocked once the flag is cleared.
```

Update the summary table (ViewModels row + test count) to include the new tests.

- [ ] **Step 3: Update TODO.md**

Find the demo-data line under Task 14 (Settings Screen) or the task list and mark the demo-data gate sub-feature as shipped, e.g. `- [x] Demo-data gate (block real entry while demo data present; Settings → Delete demo data)`. Read the file first and match its format.

- [ ] **Step 4: Commit**

```bash
git add TESTING.md TODO.md
git commit -m "docs: add demo-data gate to testing checklist and todo"
```
