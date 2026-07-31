# Demo Data & Theme Mode — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the UI detour: auto-seed realistic demo transactions (so Dashboard/Transactions render full content during development) and add a persisted `System / Light / Dark / AMOLED` theme picker in a real Settings screen.

**Architecture:** Two independent additions. (1) A temporary `DemoDataSeeder` runs at app cold start and inserts a generated batch of transactions only when the `transactions` table is empty — removable later when real SMS sync lands. (2) A DataStore-backed `ThemePreferences` exposes the theme choice as a `Flow`; `MainActivity` collects it and passes `darkTheme`/`pureBlack` into the existing `SMSExpenseTrackerTheme`; the Settings screen (currently a stub) hosts the picker. No DB migration, no schema change.

**Tech Stack:** Kotlin, Compose Material3 (1.5.0-alpha24, Expressive API opt-in), Room + KSP, Hilt, DataStore Preferences, MockK, kotlinx-coroutines-test.

## Global Constraints

- Package `com.smsexpensetracker`; min SDK 28, target 36, compile 37
- All amounts as **paisa `Long`** — never `Double`/`BigDecimal` in domain/db
- Enums stored as `name` string (DataStore string key; same convention as `Converters`)
- JUnit 4 + MockK (`every`/`coEvery`/`coVerify`); VM tests use `StandardTestDispatcher` + `Dispatchers.setMain` + `runTest` + `advanceUntilIdle`
- Build: `./gradlew assembleDebug` — Test: `./gradlew testDebugUnitTest` (no lint/typecheck)
- No code comments unless needed; follow existing file/package structure
- **No DB version bump, no schema change, no `isDemo` column** — demo seeding is temporary scaffolding
- Material icons come from `androidx-compose-material-icons-extended` (already a dependency)

---
## Task 1: DataStore foundation (ThemeMode, ThemePreferences, DI)

**Files:**
- Modify: `gradle/libs.versions.toml:1-23` (add `datastore` version + library)
- Modify: `app/build.gradle.kts:65-105` (add `implementation` line)
- Create: `app/src/main/java/com/smsexpensetracker/ui/theme/ThemeMode.kt`
- Create: `app/src/main/java/com/smsexpensetracker/core/settings/ThemePreferences.kt`
- Create: `app/src/main/java/com/smsexpensetracker/di/SettingsModule.kt`
- Test: `app/src/test/java/com/smsexpensetracker/core/settings/ThemePreferencesTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `enum ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }` in `com.smsexpensetracker.ui.theme` — used by Tasks 3 and 4.
  - `class ThemePreferences(dataStore: DataStore<Preferences>)` in `com.smsexpensetracker.core.settings` with `val themeMode: Flow<ThemeMode>` (defaults `SYSTEM`) and `suspend fun setThemeMode(mode: ThemeMode)`. Constructor-injected with the DataStore provided by `SettingsModule`. Used by Tasks 3 and 4.
  - `SettingsModule` in `com.smsexpensetracker.di` provides the `@Singleton DataStore<Preferences>` (Hilt resolves `ThemePreferences` automatically).

- [ ] **Step 1: Add the dependency to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
datastore = "1.1.7"
```
Under `[libraries]` add:
```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

- [ ] **Step 2: Add the dependency to the app module**

In `app/build.gradle.kts` inside `dependencies { }` (after the `implementation(libs.timber)` line):
```kotlin
implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 3: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/core/settings/ThemePreferencesTest.kt`:
```kotlin
package com.smsexpensetracker.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.smsexpensetracker.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ThemePreferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun createPreferences(): ThemePreferences {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
            produceFile = { tmp.newFile("test.preferences_pb") }
        )
        return ThemePreferences(dataStore)
    }

    @Test
    fun `defaults to SYSTEM`() = runTest {
        assertEquals(ThemeMode.SYSTEM, createPreferences().themeMode.first())
    }

    @Test
    fun `round trips a written mode`() = runTest {
        val prefs = createPreferences()
        prefs.setThemeMode(ThemeMode.AMOLED)
        assertEquals(ThemeMode.AMOLED, prefs.themeMode.first())
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.settings.ThemePreferencesTest"`
Expected: FAIL — compile error, `ThemeMode`/`ThemePreferences` unresolved.

- [ ] **Step 5: Create ThemeMode**

Create `app/src/main/java/com/smsexpensetracker/ui/theme/ThemeMode.kt`:
```kotlin
package com.smsexpensetracker.ui.theme

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
```

- [ ] **Step 6: Create ThemePreferences**

Create `app/src/main/java/com/smsexpensetracker/core/settings/ThemePreferences.kt`:
```kotlin
package com.smsexpensetracker.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.smsexpensetracker.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ThemePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[themeModeKey]
            ?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[themeModeKey] = mode.name }
    }
}
```

- [ ] **Step 7: Create SettingsModule**

Create `app/src/main/java/com/smsexpensetracker/di/SettingsModule.kt`:
```kotlin
package com.smsexpensetracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.settings.ThemePreferencesTest"`
Expected: PASS, 2 tests.

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/smsexpensetracker/ui/theme/ThemeMode.kt app/src/main/java/com/smsexpensetracker/core/settings/ThemePreferences.kt app/src/main/java/com/smsexpensetracker/di/SettingsModule.kt app/src/test/java/com/smsexpensetracker/core/settings/ThemePreferencesTest.kt
git commit -m "feat: add DataStore-backed theme preference with System/Light/Dark/AMOLED"
```

---
## Task 2: Demo data (DAO count, generator, seeder, Application wiring)

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt:58-62` (add `count()`)
- Create: `app/src/main/java/com/smsexpensetracker/data/demo/DemoTransactionGenerator.kt`
- Create: `app/src/main/java/com/smsexpensetracker/data/demo/DemoDataSeeder.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/SmsExpenseApp.kt`
- Test: `app/src/test/java/com/smsexpensetracker/data/demo/DemoTransactionGeneratorTest.kt`
- Test: `app/src/test/java/com/smsexpensetracker/data/demo/DemoDataSeederTest.kt`

**Interfaces:**
- Consumes: `TransactionDao` (already provided by `DatabaseModule`); `TransactionEntity`, `ParseMethod`, `TransactionType` (all exist).
- Produces:
  - `object DemoTransactionGenerator` with `fun generate(): List<TransactionEntity>` in `com.smsexpensetracker.data.demo` — used only by `DemoDataSeeder`.
  - `@Singleton class DemoDataSeeder(transactionDao: TransactionDao)` with `suspend fun seedIfEmpty()` in `com.smsexpensetracker.data.demo` — injected into `SmsExpenseApp`.

- [ ] **Step 1: Add `count()` to TransactionDao**

In `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt`, add after the `updateTransactionCategory` query (line 62):
```kotlin
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
```

- [ ] **Step 2: Write the failing generator test**

Create `app/src/test/java/com/smsexpensetracker/data/demo/DemoTransactionGeneratorTest.kt`:
```kotlin
package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.entity.TransactionType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DemoTransactionGeneratorTest {

    private val transactions = DemoTransactionGenerator.generate()

    @Test
    fun `generates at least 60 rows`() {
        assertTrue(transactions.size >= 60)
    }

    @Test
    fun `covers all seeded banks`() {
        assertTrue(transactions.map { it.bankId }.toSet().containsAll((1..5).toSet()))
    }

    @Test
    fun `covers all seeded categories`() {
        assertTrue(transactions.mapNotNull { it.categoryId }.toSet().containsAll((1..14).toSet()))
    }

    @Test
    fun `amounts are positive paisa`() {
        assertTrue(transactions.all { it.amount > 0 })
    }

    @Test
    fun `contains both credit and debit`() {
        assertTrue(transactions.any { it.type == TransactionType.CREDIT })
        assertTrue(transactions.any { it.type == TransactionType.DEBIT })
    }

    @Test
    fun `dates fall within the last 3 months`() {
        val cutoff = LocalDateTime.now().minusMonths(3)
        assertTrue(transactions.all { it.transactionDate.isAfter(cutoff) })
    }

    @Test
    fun `rows are distinct`() {
        assertTrue(transactions.size == transactions.toSet().size)
    }
}
```

- [ ] **Step 3: Write the failing seeder test**

Create `app/src/test/java/com/smsexpensetracker/data/demo/DemoDataSeederTest.kt`:
```kotlin
package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.dao.TransactionDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DemoDataSeederTest {

    private val transactionDao = mockk<TransactionDao>()

    @Test
    fun `seeds when table is empty`() = runTest {
        coEvery { transactionDao.count() } returns 0
        DemoDataSeeder(transactionDao).seedIfEmpty()
        coVerify { transactionDao.insertAll(any()) }
    }

    @Test
    fun `skips when table has rows`() = runTest {
        coEvery { transactionDao.count() } returns 5
        DemoDataSeeder(transactionDao).seedIfEmpty()
        coVerify(exactly = 0) { transactionDao.insertAll(any()) }
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.demo.*"`
Expected: FAIL — compile error, `DemoTransactionGenerator`/`DemoDataSeeder` unresolved, `count()` missing.

- [ ] **Step 5: Create DemoTransactionGenerator**

Create `app/src/main/java/com/smsexpensetracker/data/demo/DemoTransactionGenerator.kt`:
```kotlin
package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.entity.ParseMethod
import com.smsexpensetracker.core.database.entity.TransactionEntity
import com.smsexpensetracker.core.database.entity.TransactionType
import java.time.LocalDateTime

private data class DemoItem(
    val description: String,
    val bankId: Long,
    val categoryId: Long,
    val type: TransactionType,
    val amountPaisa: Long
)

object DemoTransactionGenerator {

    fun generate(): List<TransactionEntity> {
        val items = listOf(
            DemoItem("Salary - ACME Corp", 1, 12, TransactionType.CREDIT, 85_000_00),
            DemoItem("SIP - Mutual Fund", 1, 13, TransactionType.DEBIT, 10_000_00),
            DemoItem("Rent - Green Park", 1, 10, TransactionType.DEBIT, 22_000_00),
            DemoItem("BigBasket", 2, 2, TransactionType.DEBIT, 2_450_50),
            DemoItem("Blinkit", 2, 2, TransactionType.DEBIT, 780_00),
            DemoItem("Zomato", 1, 1, TransactionType.DEBIT, 460_00),
            DemoItem("Swiggy", 2, 1, TransactionType.DEBIT, 320_75),
            DemoItem("Pluxee Lunch", 5, 1, TransactionType.DEBIT, 180_00),
            DemoItem("Indian Oil", 3, 3, TransactionType.DEBIT, 2_000_00),
            DemoItem("Reliance Jio Recharge", 4, 4, TransactionType.DEBIT, 299_00),
            DemoItem("Amazon", 3, 5, TransactionType.DEBIT, 1_299_00),
            DemoItem("Refund - Flipkart", 3, 5, TransactionType.CREDIT, 950_00),
            DemoItem("Netflix", 1, 6, TransactionType.DEBIT, 649_00),
            DemoItem("Movie Tickets", 4, 6, TransactionType.DEBIT, 600_00),
            DemoItem("1mg", 5, 7, TransactionType.DEBIT, 540_00),
            DemoItem("Uber", 2, 8, TransactionType.DEBIT, 285_00),
            DemoItem("Metro Card Top-up", 3, 8, TransactionType.DEBIT, 500_00),
            DemoItem("Udemy Course", 2, 9, TransactionType.DEBIT, 1_200_00),
            DemoItem("Flight - IndiGo", 4, 11, TransactionType.DEBIT, 5_400_00),
            DemoItem("Misc Expenses", 3, 14, TransactionType.DEBIT, 350_00)
        )
        val now = LocalDateTime.now()
        return buildList {
            for (monthAgo in 2 downTo 0) {
                val monthBase = now.minusMonths(monthAgo.toLong())
                items.forEachIndexed { index, item ->
                    add(
                        TransactionEntity(
                            bankId = item.bankId,
                            amount = item.amountPaisa,
                            type = item.type,
                            description = item.description,
                            transactionDate = monthBase
                                .minusDays(index % 7L)
                                .withHour(12 + index % 8)
                                .withMinute((index * 13) % 60),
                            categoryId = item.categoryId,
                            rawSms = "",
                            smsTimestamp = 0,
                            parseMethod = ParseMethod.MANUAL
                        )
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 6: Create DemoDataSeeder**

Create `app/src/main/java/com/smsexpensetracker/data/demo/DemoDataSeeder.kt`:
```kotlin
package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.dao.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoDataSeeder @Inject constructor(
    private val transactionDao: TransactionDao
) {
    suspend fun seedIfEmpty() {
        if (transactionDao.count() == 0) {
            transactionDao.insertAll(DemoTransactionGenerator.generate())
        }
    }
}
```

- [ ] **Step 7: Wire the seeder into the Application**

Rewrite `app/src/main/java/com/smsexpensetracker/SmsExpenseApp.kt`:
```kotlin
package com.smsexpensetracker

import android.app.Application
import com.smsexpensetracker.data.demo.DemoDataSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SmsExpenseApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var demoDataSeeder: DemoDataSeeder

    override fun onCreate() {
        super.onCreate()
        appScope.launch { demoDataSeeder.seedIfEmpty() }
    }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.demo.*"`
Expected: PASS, 9 tests (7 generator + 2 seeder).

- [ ] **Step 9: Verify the app builds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (confirms Hilt wiring + new Application code compile).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt app/src/main/java/com/smsexpensetracker/data/demo app/src/main/java/com/smsexpensetracker/SmsExpenseApp.kt app/src/test/java/com/smsexpensetracker/data/demo
git commit -m "feat: seed demo transactions on first launch for UI development"
```

---
## Task 3: ThemeViewModel + MainActivity wiring

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/theme/ThemeViewModel.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/MainActivity.kt:59-84`
- Test: `app/src/test/java/com/smsexpensetracker/ui/theme/ThemeViewModelTest.kt`

**Interfaces:**
- Consumes: `ThemePreferences` (Task 1), `ThemeMode` (Task 1).
- Produces:
  - `@HiltViewModel class ThemeViewModel(themePreferences: ThemePreferences)` in `com.smsexpensetracker.ui.theme` with `val themeMode: StateFlow<ThemeMode>` and `fun onThemeModeChange(mode: ThemeMode)`. Used by `MainActivity`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/ui/theme/ThemeViewModelTest.kt`:
```kotlin
package com.smsexpensetracker.ui.theme

import com.smsexpensetracker.core.settings.ThemePreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val themePreferences = mockk<ThemePreferences>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exposes persisted theme mode`() = runTest {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.DARK)
        val viewModel = ThemeViewModel(themePreferences)
        assertEquals(ThemeMode.DARK, viewModel.themeMode.first())
    }

    @Test
    fun `change persists the selected mode`() = runTest {
        val viewModel = ThemeViewModel(themePreferences)
        viewModel.onThemeModeChange(ThemeMode.AMOLED)
        advanceUntilIdle()
        coVerify { themePreferences.setThemeMode(ThemeMode.AMOLED) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.theme.ThemeViewModelTest"`
Expected: FAIL — compile error, `ThemeViewModel` unresolved.

- [ ] **Step 3: Create ThemeViewModel**

Create `app/src/main/java/com/smsexpensetracker/ui/theme/ThemeViewModel.kt`:
```kotlin
package com.smsexpensetracker.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.settings.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.theme.ThemeViewModelTest"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Wire the theme into MainActivity**

In `app/src/main/java/com/smsexpensetracker/MainActivity.kt`, inside `setContent { SMSExpenseTrackerTheme { ... } }`, replace the bare `SMSExpenseTrackerTheme {` (line 60) with theme-mode-driven arguments. Add a `ThemeViewModel` and compute the mode before the theme call:

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.ui.theme.ThemeMode
import com.smsexpensetracker.ui.theme.ThemeViewModel
```

Then change the `setContent` block (lines 59-61) from:
```kotlin
        setContent {
            SMSExpenseTrackerTheme {
```
to:
```kotlin
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
            }
            SMSExpenseTrackerTheme(
                darkTheme = darkTheme,
                pureBlack = themeMode == ThemeMode.AMOLED
            ) {
```
The variable declarations are plain statements inside `setContent` — the brace balance is unchanged (the existing closing braces for `SMSExpenseTrackerTheme { ... }` and `setContent { ... }` still close the block).

- [ ] **Step 6: Verify the app builds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (theme change is compile-time wired; dynamic color on Android 12+ still works because `Theme.kt` only switches on the passed `darkTheme`/`pureBlack`).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/theme/ThemeViewModel.kt app/src/main/java/com/smsexpensetracker/MainActivity.kt app/src/test/java/com/smsexpensetracker/ui/theme/ThemeViewModelTest.kt
git commit -m "feat: apply persisted theme mode in MainActivity"
```

---
## Task 4: Settings screen + SettingsViewModel

**Files:**
- Rewrite: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`
- Rewrite: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `ThemePreferences` (Task 1), `ThemeMode` (Task 1).
- Produces:
  - `data class SettingsUiState(val themeMode: ThemeMode = ThemeMode.SYSTEM)`.
  - `@HiltViewModel class SettingsViewModel(themePreferences: ThemePreferences)` with `val uiState: StateFlow<SettingsUiState>` and `fun onThemeModeChange(mode: ThemeMode)`.
  - `@Composable fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel())` — same signature as before (already referenced by `NavGraph.kt:31`).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModelTest.kt`:
```kotlin
package com.smsexpensetracker.ui.screens.settings

import com.smsexpensetracker.core.settings.ThemePreferences
import com.smsexpensetracker.ui.theme.ThemeMode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val themePreferences = mockk<ThemePreferences>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exposes persisted theme mode`() = runTest {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.DARK)
        val viewModel = SettingsViewModel(themePreferences)
        assertEquals(ThemeMode.DARK, viewModel.uiState.first().themeMode)
    }

    @Test
    fun `change persists the selected mode`() = runTest {
        val viewModel = SettingsViewModel(themePreferences)
        viewModel.onThemeModeChange(ThemeMode.AMOLED)
        advanceUntilIdle()
        coVerify { themePreferences.setThemeMode(ThemeMode.AMOLED) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.settings.SettingsViewModelTest"`
Expected: FAIL — compile error, `SettingsViewModel`/`SettingsUiState` unresolved.

- [ ] **Step 3: Rewrite SettingsViewModel**

Rewrite `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModel.kt`:
```kotlin
package com.smsexpensetracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.settings.ThemePreferences
import com.smsexpensetracker.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = themePreferences.themeMode
        .map { SettingsUiState(themeMode = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.settings.SettingsViewModelTest"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Rewrite SettingsScreen**

Rewrite `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`:
```kotlin
package com.smsexpensetracker.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.size(24.dp))

        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.size(8.dp))

        ThemeModeRow(
            icon = Icons.Outlined.BrightnessAuto,
            label = "System",
            selected = state.themeMode == ThemeMode.SYSTEM,
            onClick = { viewModel.onThemeModeChange(ThemeMode.SYSTEM) }
        )
        ThemeModeRow(
            icon = Icons.Outlined.WbSunny,
            label = "Light",
            selected = state.themeMode == ThemeMode.LIGHT,
            onClick = { viewModel.onThemeModeChange(ThemeMode.LIGHT) }
        )
        ThemeModeRow(
            icon = Icons.Outlined.Nightlight,
            label = "Dark",
            selected = state.themeMode == ThemeMode.DARK,
            onClick = { viewModel.onThemeModeChange(ThemeMode.DARK) }
        )
        ThemeModeRow(
            icon = Icons.Outlined.DarkMode,
            label = "AMOLED",
            subtitle = "Pure black background",
            selected = state.themeMode == ThemeMode.AMOLED,
            onClick = { viewModel.onThemeModeChange(ThemeMode.AMOLED) }
        )

        Spacer(modifier = Modifier.size(32.dp))

        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = "SMS Expense Tracker",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Version 1.0",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeModeRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
```

- [ ] **Step 6: Run the full unit test suite**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest`
Expected: PASS — 117 existing + 15 new = 132 tests, 0 failures.

- [ ] **Step 7: Verify the app builds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/settings app/src/test/java/com/smsexpensetracker/ui/screens/settings
git commit -m "feat: build Settings screen with theme picker and about section"
```

---
## Task 5: Manual smoke test (needs a device/emulator — human partner)

**Files:** none.

- [ ] **Step 1: Fresh-install smoke test**

On an emulator or device: `./gradlew installDebug`, then:
1. First launch shows the Dashboard with populated summary cards, charts, and 5 recent transactions (demo data auto-seeded).
2. Transactions tab lists demo entries across months; search/filter/bank chips work.
3. Tap + FAB → Manual Entry → save an entry → it appears in the list alongside demo data.

- [ ] **Step 2: Theme smoke test**

1. Settings tab → Appearance → tap each of System / Light / Dark / AMOLED and confirm the UI switches immediately (AMOLED = pure-black background).
2. Set AMOLED, kill the app, relaunch → AMOLED is still applied (persisted).
3. Set System → toggle the OS dark/light mode → app follows.

- [ ] **Step 3: Report results**

Report what you observed, including anything that did not match, before considering this detour done.
