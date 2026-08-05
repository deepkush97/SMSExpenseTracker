# New-User Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give a new user a one-time 3-page welcome flow (what the app does, how to start, Dashboard tour) with immediate actions — try demo data or sync real SMS — plus a Dashboard "Get started" card as the persistent safety net for skippers.

**Architecture:** `MainActivity` gates at the root: a DataStore-backed `onboarding_complete` flag switches between a new `OnboardingScreen` (3-page `HorizontalPager`) and the existing Scaffold+NavHost. A shared `OnboardingActionsViewModel` (demo seed, sync, demo-barrier, mark-complete) is used by both the onboarding flow and the Dashboard card. Permission logic (READ_SMS + RECEIVE_SMS) is extracted from `TransactionsScreen` into a reusable `rememberSmsSyncPermission` composable.

**Tech Stack:** Kotlin, Compose (Material3, `HorizontalPager` from compose-foundation 1.7.8), Hilt, DataStore (existing `settings` store), Room (existing), JUnit 4 + MockK + `runTest`.

## Global Constraints

- Package: `com.smsexpensetracker`; min SDK 28 / target 36 / compile 37.
- **No new dependencies.** `HorizontalPager` is in the existing Compose foundation; `Icons.Filled.Close` is in the included material-icons. Do not add anything to `libs.versions.toml` or `build.gradle.kts`.
- Money is paisa `Long`; never `Double`/`BigDecimal`. (No money math in this plan beyond passing through existing APIs.)
- TDD: write the failing test first, verify it fails, then implement. Only Tasks 1–2 have unit tests (DataStore + ViewModel); Tasks 3–6 are UI/refactor and are verified by the build + full test gate + manual acceptance.
- Test gate: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug` — **398 existing tests must stay green**, plus new ones (total 407).
- `CancellationException` is always re-thrown (existing convention).
- Commit per task, conventional messages (`feat:`/`refactor:`/`docs:`) matching repo history. Never commit on another task's files.
- Onboarding UI text is fixed in this plan — do not improvise copy.

---

## File Structure

- **Create** `app/src/main/java/com/smsexpensetracker/core/settings/OnboardingPreferences.kt` — DataStore-backed `onboarding_complete` flag.
- **Test** `app/src/test/java/com/smsexpensetracker/core/settings/OnboardingPreferencesTest.kt` — mirrors `DemoDataPreferencesTest`.
- **Create** `app/src/main/java/com/smsexpensetracker/ui/onboarding/OnboardingActionsViewModel.kt` — demo seed / sync / barrier / mark-complete.
- **Test** `app/src/test/java/com/smsexpensetracker/ui/onboarding/OnboardingActionsViewModelTest.kt` — MockK.
- **Create** `app/src/main/java/com/smsexpensetracker/ui/components/SmsSyncPermission.kt` — `rememberSmsSyncPermission(onGranted, onDenied)`.
- **Modify** `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt` — use the shared permission helper (behavior-preserving refactor).
- **Create** `app/src/main/java/com/smsexpensetracker/ui/onboarding/OnboardingScreen.kt` — 3-page pager.
- **Modify** `app/src/main/java/com/smsexpensetracker/MainActivity.kt` — first-launch gate.
- **Modify** `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardScreen.kt` — "Get started" card.
- **Modify** `TESTING.md`, `TODO.md` — docs.

---

### Task 1: `OnboardingPreferences` (DataStore flag)

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/core/settings/OnboardingPreferences.kt`
- Test: `app/src/test/java/com/smsexpensetracker/core/settings/OnboardingPreferencesTest.kt`

**Interfaces:**
- Consumes: the existing `@Inject constructor(dataStore: DataStore<Preferences>)` pattern from `DemoDataPreferences.kt` (provided by `di/SettingsModule.kt`).
- Produces: `class OnboardingPreferences` with `val onboardingComplete: Flow<Boolean>` and `suspend fun setOnboardingComplete(complete: Boolean)`. Used by Tasks 2, 5.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/core/settings/OnboardingPreferencesTest.kt`:

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingPreferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun createPreferences(): OnboardingPreferences {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
            produceFile = { tmp.newFile("test.preferences_pb") }
        )
        return OnboardingPreferences(dataStore)
    }

    @Test
    fun `defaults to false`() = runTest {
        assertFalse(createPreferences().onboardingComplete.first())
    }

    @Test
    fun `round trips a written true value`() = runTest {
        val prefs = createPreferences()
        prefs.setOnboardingComplete(true)
        assertTrue(prefs.onboardingComplete.first())
    }

    @Test
    fun `round trips a written false value`() = runTest {
        val prefs = createPreferences()
        prefs.setOnboardingComplete(true)
        prefs.setOnboardingComplete(false)
        assertFalse(prefs.onboardingComplete.first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.settings.OnboardingPreferencesTest" -i`
Expected: FAIL with compilation error `Unresolved reference: OnboardingPreferences`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/smsexpensetracker/core/settings/OnboardingPreferences.kt`:

```kotlin
package com.smsexpensetracker.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OnboardingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val onboardingCompleteKey = booleanPreferencesKey("onboarding_complete")

    val onboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[onboardingCompleteKey] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { prefs -> prefs[onboardingCompleteKey] = complete }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.settings.OnboardingPreferencesTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Run the full test gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 401 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/settings/OnboardingPreferences.kt app/src/test/java/com/smsexpensetracker/core/settings/OnboardingPreferencesTest.kt
git commit -m "feat: add onboarding_complete DataStore preference"
```

---

### Task 2: `OnboardingActionsViewModel`

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/onboarding/OnboardingActionsViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/onboarding/OnboardingActionsViewModelTest.kt`

**Interfaces:**
- Consumes: `OnboardingPreferences` (Task 1: `onboardingComplete`, `setOnboardingComplete`); existing `DemoDataSeeder.seedIfEmpty(): Int` / `deleteDemoData()`; `SmsSyncUseCase.sync(): SyncResult` (suspend); `DemoDataPreferences.demoDataLoaded: Flow<Boolean>`.
- Produces: `OnboardingActionsUiState(isBusy: Boolean = false, showDemoBarrier: Boolean = false, demoLoaded: Boolean = false)` and `OnboardingActionsViewModel` with `uiState: StateFlow<OnboardingActionsUiState>`, `loadDemoData()`, `sync()`, `markComplete()`, `dismissDemoBarrier()`, `confirmDeleteDemoData()`. Used by Tasks 4 and 6.

Design note (deviation from spec §6, intentional): failures do NOT carry a user-facing message. `setOnboardingComplete(true)` flips the `MainActivity` gate, unmounting whichever composable could have shown a snackbar. Instead, failures reset `isBusy`; the onboarding flow or Dashboard card simply returns to idle and the user can retry.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/ui/onboarding/OnboardingActionsViewModelTest.kt`:

```kotlin
package com.smsexpensetracker.ui.onboarding

import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.core.settings.OnboardingPreferences
import com.smsexpensetracker.data.demo.DemoDataSeeder
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import com.smsexpensetracker.domain.value.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingActionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val demoDataSeeder = mockk<DemoDataSeeder>()
    private val smsSyncUseCase = mockk<SmsSyncUseCase>()
    private val demoDataPreferences = mockk<DemoDataPreferences>()
    private val onboardingPreferences = mockk<OnboardingPreferences>()
    private val demoDataLoadedFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { demoDataPreferences.demoDataLoaded } returns demoDataLoadedFlow
        coEvery { onboardingPreferences.setOnboardingComplete(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        OnboardingActionsViewModel(demoDataSeeder, smsSyncUseCase, demoDataPreferences, onboardingPreferences)

    @Test
    fun `loadDemoData seeds and marks onboarding complete`() = runTest(testDispatcher) {
        coEvery { demoDataSeeder.seedIfEmpty() } returns 60
        val viewModel = viewModel()

        viewModel.loadDemoData()
        advanceUntilIdle()

        coVerify(exactly = 1) { demoDataSeeder.seedIfEmpty() }
        coVerify(exactly = 1) { onboardingPreferences.setOnboardingComplete(true) }
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `loadDemoData marks complete even when seeding fails`() = runTest(testDispatcher) {
        coEvery { demoDataSeeder.seedIfEmpty() } throws RuntimeException("disk full")
        val viewModel = viewModel()

        viewModel.loadDemoData()
        advanceUntilIdle()

        coVerify(exactly = 1) { onboardingPreferences.setOnboardingComplete(true) }
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `sync runs the use case and marks onboarding complete`() = runTest(testDispatcher) {
        coEvery { smsSyncUseCase.sync() } returns SyncResult(scanned = 5, inserted = 3, unparsed = 1)
        val viewModel = viewModel()

        viewModel.sync()
        advanceUntilIdle()

        coVerify(exactly = 1) { smsSyncUseCase.sync() }
        coVerify(exactly = 1) { onboardingPreferences.setOnboardingComplete(true) }
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `sync with demo data present shows barrier and does not run sync`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.sync()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showDemoBarrier)
        coVerify(exactly = 0) { smsSyncUseCase.sync() }
        coVerify(exactly = 0) { onboardingPreferences.setOnboardingComplete(true) }
    }

    @Test
    fun `markComplete sets onboarding complete`() = runTest(testDispatcher) {
        val viewModel = viewModel()

        viewModel.markComplete()
        advanceUntilIdle()

        coVerify(exactly = 1) { onboardingPreferences.setOnboardingComplete(true) }
    }

    @Test
    fun `confirmDeleteDemoData deletes demo and closes barrier`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        coEvery { demoDataSeeder.deleteDemoData() } returns Unit
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.confirmDeleteDemoData()
        advanceUntilIdle()

        coVerify(exactly = 1) { demoDataSeeder.deleteDemoData() }
        assertFalse(viewModel.uiState.value.showDemoBarrier)
        assertFalse(viewModel.uiState.value.demoLoaded)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.onboarding.OnboardingActionsViewModelTest" -i`
Expected: FAIL with compilation error `Unresolved reference: OnboardingActionsViewModel`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/smsexpensetracker/ui/onboarding/OnboardingActionsViewModel.kt`:

```kotlin
package com.smsexpensetracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.core.settings.OnboardingPreferences
import com.smsexpensetracker.data.demo.DemoDataSeeder
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingActionsUiState(
    val isBusy: Boolean = false,
    val showDemoBarrier: Boolean = false,
    val demoLoaded: Boolean = false
)

@HiltViewModel
class OnboardingActionsViewModel @Inject constructor(
    private val demoDataSeeder: DemoDataSeeder,
    private val smsSyncUseCase: SmsSyncUseCase,
    private val demoDataPreferences: DemoDataPreferences,
    private val onboardingPreferences: OnboardingPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingActionsUiState())
    val uiState: StateFlow<OnboardingActionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            demoDataPreferences.demoDataLoaded.collect { loaded ->
                _uiState.update { it.copy(demoLoaded = loaded) }
            }
        }
    }

    fun loadDemoData() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                demoDataSeeder.seedIfEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // retry is available from the onboarding flow or Dashboard card
            } finally {
                onboardingPreferences.setOnboardingComplete(true)
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun sync() {
        if (_uiState.value.isBusy) return
        if (_uiState.value.demoLoaded) {
            _uiState.update { it.copy(showDemoBarrier = true) }
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                smsSyncUseCase.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // retry is available from the onboarding flow or Dashboard card
            } finally {
                onboardingPreferences.setOnboardingComplete(true)
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun markComplete() {
        viewModelScope.launch {
            onboardingPreferences.setOnboardingComplete(true)
        }
    }

    fun dismissDemoBarrier() = _uiState.update { it.copy(showDemoBarrier = false) }

    fun confirmDeleteDemoData() {
        viewModelScope.launch {
            demoDataSeeder.deleteDemoData()
            _uiState.update { it.copy(showDemoBarrier = false, demoLoaded = false) }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.onboarding.OnboardingActionsViewModelTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Run the full test gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 407 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/onboarding/OnboardingActionsViewModel.kt app/src/test/java/com/smsexpensetracker/ui/onboarding/OnboardingActionsViewModelTest.kt
git commit -m "feat: add shared onboarding actions view model"
```

---

### Task 3: Shared SMS-permission helper + `TransactionsScreen` refactor

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/components/SmsSyncPermission.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt` (lines 77-114 permission block, 277-294 rationale dialog)

**Interfaces:**
- Consumes: existing `PermissionManager` (`hasPermission(context)`, `shouldShowRationale(activity)`, `openSettings(context)`).
- Produces: `@Composable fun rememberSmsSyncPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit` — returns a `requestSync` lambda. Used by Tasks 4 and 6. Behavior-preserving refactor of the current `TransactionsScreen` wiring (requests `READ_SMS` + `RECEIVE_SMS` together).

- [ ] **Step 1: Create the helper**

Create `app/src/main/java/com/smsexpensetracker/ui/components/SmsSyncPermission.kt`:

```kotlin
package com.smsexpensetracker.ui.components

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.smsexpensetracker.data.sms.PermissionManager

@Composable
fun rememberSmsSyncPermission(
    onGranted: () -> Unit,
    onDenied: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager() }
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants[Manifest.permission.READ_SMS] == true &&
            grants[Manifest.permission.RECEIVE_SMS] == true
        if (allGranted) onGranted() else onDenied()
    }

    val requestSync: () -> Unit = {
        when {
            permissionManager.hasPermission(context) -> onGranted()
            permissionManager.shouldShowRationale(context as? Activity) -> showRationale = true
            else -> launcher.launch(
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
            )
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Allow SMS access?") },
            text = {
                Text("SMS Expense Tracker reads your bank SMS to extract transaction details. SMS data stays on your device.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(
                        arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                    )
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now") }
            }
        )
    }

    return requestSync
}
```

- [ ] **Step 2: Refactor `TransactionsScreen` to use it**

In `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt`:

**(a)** Delete the permission block currently at lines 77-114. That block is:

```kotlin
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionManager = remember { PermissionManager() }
    var showRationale by remember { mutableStateOf(false) }

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

    fun beginSync() {
        if (permissionManager.hasPermission(context)) {
            viewModel.sync()
        } else if (permissionManager.shouldShowRationale(context as? Activity)) {
            showRationale = true
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
            )
        }
    }
```

Replace it with:

```kotlin
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionManager = remember { PermissionManager() }

    val requestSync = rememberSmsSyncPermission(
        onGranted = { viewModel.sync() },
        onDenied = {
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
    )
```

**(b)** Replace the two call sites of `beginSync()` with `requestSync`:
- Line 138: `onAction = { beginSync() }` → `onAction = requestSync`
- Line 172: `onClick = { beginSync() }` → `onClick = requestSync`

**(c)** Delete the rationale `AlertDialog` block at lines 277-294:

```kotlin
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Allow SMS access?") },
            text = { Text("SMS Expense Tracker reads your bank SMS to extract transaction details. SMS data stays on your device.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                    )
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now") }
            }
        )
    }
```

**(d)** Remove now-unused imports: `android.Manifest` (line 3), `android.app.Activity` (line 4), `androidx.activity.compose.rememberLauncherForActivityResult` (line 8), `androidx.activity.result.contract.ActivityResultContracts` (line 9), `androidx.compose.material3.AlertDialog` (line 27), `androidx.compose.runtime.mutableStateOf` (line 47), `androidx.compose.runtime.setValue` (line 50). Keep `android.content.Intent` / `android.net.Uri` if they are used elsewhere in the file (they are not, but verify; unused imports are only warnings, not build failures). Add `import com.smsexpensetracker.ui.components.rememberSmsSyncPermission`.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. If unused-import warnings appear, remove those imports.

- [ ] **Step 4: Run the full test gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 407 tests pass (behavior unchanged).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/components/SmsSyncPermission.kt app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt
git commit -m "refactor: extract shared SMS permission helper"
```

---

### Task 4: `OnboardingScreen` (3-page pager)

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/onboarding/OnboardingScreen.kt`

**Interfaces:**
- Consumes: `OnboardingActionsViewModel` (Task 2), `rememberSmsSyncPermission` (Task 3), `DemoDataBarrierDialog` (existing, `ui/components/DemoDataBarrierDialog.kt`).
- Produces: `@Composable fun OnboardingScreen(modifier: Modifier = Modifier, viewModel: OnboardingActionsViewModel = hiltViewModel())`. Used by Task 5.

- [ ] **Step 1: Create the screen**

Create `app/src/main/java/com/smsexpensetracker/ui/onboarding/OnboardingScreen.kt`:

```kotlin
package com.smsexpensetracker.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.smsexpensetracker.ui.components.DemoDataBarrierDialog
import com.smsexpensetracker.ui.components.rememberSmsSyncPermission
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingActionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val requestSync = rememberSmsSyncPermission(
        onGranted = { viewModel.sync() },
        onDenied = { viewModel.markComplete() }
    )

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { viewModel.markComplete() }) {
                    Text("Skip")
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> IntroPage()
                    1 -> PathsPage()
                    else -> DashboardTourPage(
                        isBusy = state.isBusy,
                        onDemoData = viewModel::loadDemoData,
                        onSyncSms = requestSync
                    )
                }
            }
            PageIndicator(pageCount = 3, currentPage = pagerState.currentPage)
            Spacer(Modifier.height(16.dp))
            if (pagerState.currentPage < 2) {
                Button(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Next")
                }
            } else {
                Spacer(Modifier.height(52.dp))
            }
        }
    }

    if (state.showDemoBarrier) {
        DemoDataBarrierDialog(
            onConfirmDelete = viewModel::confirmDeleteDemoData,
            onDismiss = viewModel::dismissDemoBarrier
        )
    }
}

@Composable
private fun IntroPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SMS Expense Tracker",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Your bank SMS become categorized transactions. No manual entry, no receipts to type.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Your SMS data stays on your device.",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No account, no upload, no internet needed.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PathsPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Two ways to start",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Try sample data",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Explore with 60 sample transactions. You can delete them anytime from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Sync your real SMS",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Connect HDFC, ICICI, DCB, and Pluxee bank SMS. New bank SMS are captured automatically.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardTourPage(
    isBusy: Boolean,
    onDemoData: () -> Unit,
    onSyncSms: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Total Spent / Total Received cards",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Charts: spending by bank, monthly trend, and category breakdown.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Recent transactions with category colors and bank names.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        if (isBusy) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = onDemoData,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Try with demo data")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSyncSms,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Sync my SMS")
            }
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full test gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 407 tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/onboarding/OnboardingScreen.kt
git commit -m "feat: add first-launch onboarding screen"
```

---

### Task 5: `MainActivity` first-launch gate

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/MainActivity.kt`

**Interfaces:**
- Consumes: `OnboardingPreferences.onboardingComplete: Flow<Boolean>` (Task 1), `OnboardingScreen()` (Task 4).
- Produces: the Activity switches between onboarding and the main Scaffold; no new public API.

- [ ] **Step 1: Add field injection and the gate**

In `app/src/main/java/com/smsexpensetracker/MainActivity.kt`:

**(a)** Add imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.smsexpensetracker.core.settings.OnboardingPreferences
import com.smsexpensetracker.ui.onboarding.OnboardingScreen
import javax.inject.Inject
```

(`getValue`, `remember`, `collectAsState` are already imported.)

**(b)** Change the class so it owns the injected preference and a new `@Composable private fun MainScaffold()` (the existing Scaffold+NavHost body, unchanged), and the `onCreate` becomes a gate. Use a nullable `remember` state + `LaunchedEffect` for the flag: `onboardingComplete` is `Flow<Boolean>` (non-null), so `collectAsState(initial = null)` would not compile — the manual state starts `null` ("still loading") until the first DataStore emission:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
            }
            var onboardingComplete by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) {
                onboardingPreferences.onboardingComplete.collect { onboardingComplete = it }
            }
            SMSExpenseTrackerTheme(
                darkTheme = darkTheme,
                pureBlack = themeMode == ThemeMode.AMOLED
            ) {
                when (onboardingComplete) {
                    null -> Unit
                    false -> OnboardingScreen()
                    true -> MainScaffold()
                }
            }
        }
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute in BottomNavItem.items.map { it.route }) {
                PillNavigationBar(
                    items = BottomNavItem.items,
                    currentRoute = currentRoute,
                    onItemClick = { item ->
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        AppNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}
```

The `setContent` body replaces the existing lines 64-100; `MainScaffold` is the existing `Scaffold { … AppNavHost(…) }` block moved verbatim into its own composable. `collectAsState`, `rememberNavController`, `currentBackStackEntryAsState`, and all UI imports are already present in the file.

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full test gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 407 tests pass.

- [ ] **Step 4: Manual verification (emulator)**

Install: `./gradlew installDebug`
- Fresh install (clear app data first) → 3-page welcome flow appears; bottom nav bar is hidden.
- Tap Skip → Dashboard with "Get started" card; relaunch → welcome does not reappear.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/MainActivity.kt
git commit -m "feat: gate first launch behind onboarding flow"
```

---

### Task 6: Dashboard "Get started" card

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: `OnboardingActionsViewModel` (Task 2), `rememberSmsSyncPermission` (Task 3). `state.recentTransactions` already exists on `DashboardUiState`.
- Produces: a `GetStartedCard` shown when no transactions exist; no new public API.

- [ ] **Step 1: Modify `DashboardScreen`**

In `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardScreen.kt`:

**(a)** Add imports:

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.smsexpensetracker.ui.components.rememberSmsSyncPermission
import com.smsexpensetracker.ui.onboarding.OnboardingActionsViewModel
```

**(b)** Add a second ViewModel param and the card wiring to `DashboardScreen`:

```kotlin
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onTransactionClick: (Long) -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingActionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    var showGetStartedCard by remember { mutableStateOf(true) }

    val requestSync = rememberSmsSyncPermission(
        onGranted = { onboardingViewModel.sync() },
        onDenied = {}
    )

    if (state.isLoading) {
        // ... unchanged CircularProgressIndicator block ...
    }

    LazyColumn(...) {
        if (showGetStartedCard && state.recentTransactions.isEmpty()) {
            item(key = "getStarted") {
                GetStartedCard(
                    isBusy = onboardingState.isBusy,
                    onDemoData = onboardingViewModel::loadDemoData,
                    onSyncSms = requestSync,
                    onDismiss = { showGetStartedCard = false }
                )
            }
        }
        // ... existing items ...
    }
}
```

**(c)** Add the private card composable at the bottom of the file (after `DashboardScreen`):

```kotlin
@Composable
private fun GetStartedCard(
    isBusy: Boolean,
    onDemoData: () -> Unit,
    onSyncSms: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Get started",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                }
            }
            Text(
                text = "Load sample data to explore, or sync your real bank SMS.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onDemoData,
                        modifier = Modifier.weight(1f)
                    ) { Text("Try demo data") }
                    OutlinedButton(
                        onClick = onSyncSms,
                        modifier = Modifier.weight(1f)
                    ) { Text("Sync SMS") }
                }
            }
        }
    }
}
```

The `GetStartedCard` item must be inserted as the FIRST item in the `LazyColumn` (before the existing summary-cards `item { }` block at line 59). All other `LazyColumn` items stay unchanged.

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full test gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 407 tests pass.

- [ ] **Step 4: Manual verification (emulator)**

- Skip onboarding → Dashboard shows "Get started" card above the zeroed summary cards; X hides it for the session.
- Tap "Try demo data" → 60 rows appear and the card disappears.
- Clear app data → card reappears.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardScreen.kt
git commit -m "feat: add get-started card to empty dashboard"
```

---

### Task 7: Docs (`TESTING.md` + `TODO.md`)

**Files:**
- Modify: `TESTING.md`, `TODO.md`

- [ ] **Step 1: Update `TESTING.md` §1**

Replace the first bullet of `## 1. First Launch & Navigation` (currently says "Fresh install → app opens on **Dashboard**, NOT an onboarding screen."):

```markdown
- [ ] Fresh install → app opens on the **3-page welcome flow** (what the app does → two ways to start → Dashboard tour; bottom nav bar hidden). Tap **Skip** → Dashboard with the **Get started** card; relaunching does not show the welcome again.
```

- [ ] **Step 2: Update `TESTING.md` §2**

Add a row at the top of `## 2. Dashboard` (before the "assume data is present" note):

```markdown
- [ ] With an empty DB, Dashboard shows a **Get started** card above the zeroed summary cards with **Try demo data** + **Sync SMS** actions and an X to dismiss (per session). The card disappears once any transaction exists.
```

- [ ] **Step 3: Update `TODO.md`**

- Mark **F5. Dashboard empty state** (`TODO.md` line 20) done:

```markdown
- [x] **F5. Dashboard empty state** — "Get started" card on empty Dashboard (Try demo data / Sync SMS / dismiss) doubles as onboarding safety net. (Task 10; implemented with the new-user onboarding flow, see `docs/superpowers/specs/2026-08-05-new-user-onboarding-design.md`)
```

- Mark the two checklist items done: line 132 "Implement Dashboard empty state" and line 193 "Implement first-launch detection":

```markdown
- [x] Implement Dashboard empty state (`GetStartedCard` on empty Dashboard) → **F5**
- [x] Implement first-launch detection (DataStore `onboarding_complete` flag) → **F5** (3-page welcome flow at first launch; Dashboard card is the safety net)
```

- [ ] **Step 4: Run the full test gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 407 tests pass.

- [ ] **Step 5: Commit**

```bash
git add TESTING.md TODO.md
git commit -m "docs: mark F5 done, document onboarding in QA checklist"
```

---

## Self-Review

- **Spec coverage:**
  - §4.1 OnboardingPreferences → Task 1
  - §4.2 OnboardingActionsViewModel → Task 2
  - §4.3 OnboardingScreen (3 pages + dots + skip + busy + barrier) → Task 4
  - §4.4 shared SMS-permission helper + TransactionsScreen refactor → Task 3
  - §4.5 MainActivity gate → Task 5
  - §4.6 Dashboard Get-started card (dismiss = per-session `remember`) → Task 6
  - §8 tests (OnboardingPreferencesTest, OnboardingActionsViewModelTest) → Tasks 1–2
  - §8 docs (TESTING.md §1/§2, TODO.md F5) → Task 7
  - Acceptance criteria 1-5 → Tasks 4-6 + manual verification steps; criterion 6 → the test gate runs at every task.
- **Deviation logged:** spec §6's snackbar messages are omitted (documented in Task 2) because the gate unmounts the screen at the moment a message would be shown.
- **Placeholder scan:** no TBD/TODO; every code step is complete source.
- **Type consistency:** `OnboardingPreferences.onboardingComplete` / `setOnboardingComplete(complete: Boolean)`, `OnboardingActionsUiState(isBusy, showDemoBarrier, demoLoaded)`, `rememberSmsSyncPermission(onGranted, onDenied): () -> Unit`, `OnboardingScreen(modifier, viewModel)` — used identically across all tasks.
