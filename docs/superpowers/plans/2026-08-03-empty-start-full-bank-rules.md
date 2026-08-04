# Empty-Start App + Full Bank Rule Set — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a fresh install start with zero transactions (demo data becomes an opt-in Settings action) and seed a full 14-rule template set for HDFC/ICICI/Pluxee/DCB Bank so every real SMS in `push_test_sms.sh` parses out of the box.

**Architecture:** Two independent changes. (1) Remove the auto-seed call in `SmsExpenseApp.onCreate`, make `DemoDataSeeder.seedIfEmpty()` return the inserted count, add a Settings "Load demo data" row wired through `SettingsViewModel`. (2) In `SeedDatabaseCallback`, add DCB Bank (id 6, sender `DCBANK`) and replace the 6 regex seed rules with 14 template rules (converting the 6, adding 8). No Room migration — new seeds reach fresh installs only.

**Tech Stack:** Kotlin, Compose, Hilt, Room, JUnit 4 + MockK + kotlinx-coroutines-test.

## Global Constraints

- Package root: `com.smsexpensetracker`. SDK min 28 / target 36 / compile 37.
- All money amounts as **paisa `Long`** — never `Double`/`BigDecimal`.
- No code comments unless the task's code block explicitly includes them.
- Seed templates must be **verbatim** the patterns already proven in `RegexParserTest` (the "TPL" rows) — do not "improve" them.
- Rule seed inserts keep explicit ids 1–14; apostrophes doubled via `.replace("'", "''")`.
- Sync matches SMS to rules by **pattern only** (`getAllRules()` ordered by `bankId, description`, first-match-wins). The `smsSender` column is only used by the Parser screen auto-detect.
- Build gate: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug` (no lint/typecheck configured).
- Commit directly to `main`. NEVER stage `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardViewModel.kt`, `opencode.json`, or any `*.db` file.

---

### Task 1: Remove the auto-seed on app launch

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/SmsExpenseApp.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `SmsExpenseApp` with no coroutine scope and no `DemoDataSeeder` injection. Later tasks rely on `DemoDataSeeder` still being a `@Singleton` injectable class (it stays exactly as-is in this task).

- [ ] **Step 1: Verify the current file**

Current `SmsExpenseApp.kt` (lines 1–29) injects `DemoDataSeeder` and launches `demoDataSeeder.seedIfEmpty()` in `appScope`. Confirm `appScope` is used nowhere else (grep for `appScope` in the file — only line 27 references it).

- [ ] **Step 2: Rewrite the file**

Replace the whole file with:

```kotlin
package com.smsexpensetracker

import android.app.Application
import com.smsexpensetracker.data.logging.LoggingSetup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SmsExpenseApp : Application() {

    @Inject
    lateinit var loggingSetup: LoggingSetup

    override fun onCreate() {
        super.onCreate()
        loggingSetup.install()
    }
}
```

(Removes the now-unused `appScope`, `demoDataSeeder`, and the imports `DemoDataSeeder`, `CoroutineScope`, `Dispatchers`, `SupervisorJob`, `launch`.)

- [ ] **Step 3: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL — the existing 306 tests still pass (no test referenced the app auto-seed; `DemoDataSeederTest` tests the seeder directly).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/SmsExpenseApp.kt
git commit -m "feat: start with an empty transaction list (no auto demo seed)"
```

---

### Task 2: Make the demo seeder report what it inserted

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/data/demo/DemoDataSeeder.kt`
- Test: `app/src/test/java/com/smsexpensetracker/data/demo/DemoDataSeederTest.kt`

**Interfaces:**
- Consumes: `TransactionDao` with `suspend fun count(): Int` and `suspend fun insertAll(transactions: List<TransactionEntity>)`; `DemoTransactionGenerator.generate(): List<TransactionEntity>` (60 items).
- Produces: `suspend fun seedIfEmpty(): Int` — returns the number of transactions inserted (60), or `0` if the table already had rows. Task 3 consumes this return value.

- [ ] **Step 1: Write the failing tests**

Replace `app/src/test/java/com/smsexpensetracker/data/demo/DemoDataSeederTest.kt` with:

```kotlin
package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.dao.TransactionDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoDataSeederTest {

    private val transactionDao = mockk<TransactionDao>()

    @Test
    fun `seeds when table is empty and returns inserted count`() = runTest {
        coEvery { transactionDao.count() } returns 0
        coEvery { transactionDao.insertAll(any()) } returns Unit
        val inserted = DemoDataSeeder(transactionDao).seedIfEmpty()
        coVerify { transactionDao.insertAll(any()) }
        assertEquals(DemoTransactionGenerator.generate().size, inserted)
    }

    @Test
    fun `skips when table has rows and returns zero`() = runTest {
        coEvery { transactionDao.count() } returns 5
        val inserted = DemoDataSeeder(transactionDao).seedIfEmpty()
        coVerify(exactly = 0) { transactionDao.insertAll(any()) }
        assertEquals(0, inserted)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.demo.DemoDataSeederTest"`
Expected: FAIL — `seedIfEmpty()` still returns `Unit`; `inserted` is `Unit`, so `assertEquals` fails to compile or the return-type mismatch errors.

- [ ] **Step 3: Implement the return value**

Replace `seedIfEmpty()` in `DemoDataSeeder.kt`:

```kotlin
    suspend fun seedIfEmpty(): Int {
        if (transactionDao.count() == 0) {
            val transactions = DemoTransactionGenerator.generate()
            transactionDao.insertAll(transactions)
            return transactions.size
        }
        return 0
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.demo.DemoDataSeederTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/data/demo/DemoDataSeeder.kt app/src/test/java/com/smsexpensetracker/data/demo/DemoDataSeederTest.kt
git commit -m "feat: demo seeder reports inserted transaction count"
```

---

### Task 3: SettingsViewModel — load demo data action

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `DemoDataSeeder.seedIfEmpty(): Int` (Task 2).
- Produces: `SettingsUiState.demoMessage: String?` and `SettingsUiState.isDemoBusy: Boolean`; `fun loadDemoData()`; `fun consumeDemoMessage()`. Task 4 reads `demoMessage` for the snackbar.

- [ ] **Step 1: Write the failing test additions**

Add a `demoDataSeeder` mock to `SettingsViewModelTest`, update the constructor call, and add three tests. In the class body, add alongside the existing mocks:

```kotlin
    private val demoDataSeeder = mockk<DemoDataSeeder>()
```

Replace the `viewModel()` helper:

```kotlin
    private fun viewModel() =
        SettingsViewModel(themePreferences, exportCsvUseCase, importCsvUseCase, demoDataSeeder)
```

Append before the closing brace of the class:

```kotlin
    @Test
    fun `loadDemoData shows loaded message when seeder inserts`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { demoDataSeeder.seedIfEmpty() } returns 60
        val viewModel = viewModel()

        viewModel.loadDemoData()
        advanceUntilIdle()

        assertEquals("Loaded 60 demo transactions", viewModel.uiState.value.demoMessage)
        assertFalse(viewModel.uiState.value.isDemoBusy)
    }

    @Test
    fun `loadDemoData shows already-loaded message when seeder skips`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { demoDataSeeder.seedIfEmpty() } returns 0
        val viewModel = viewModel()

        viewModel.loadDemoData()
        advanceUntilIdle()

        assertEquals("Demo data already loaded", viewModel.uiState.value.demoMessage)
        assertFalse(viewModel.uiState.value.isDemoBusy)
    }

    @Test
    fun `consumeDemoMessage clears the message`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { demoDataSeeder.seedIfEmpty() } returns 60
        val viewModel = viewModel()
        viewModel.loadDemoData()
        advanceUntilIdle()

        viewModel.consumeDemoMessage()

        assertNull(viewModel.uiState.value.demoMessage)
    }

    @Test
    fun `loadDemoData surfaces a failure message`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { demoDataSeeder.seedIfEmpty() } throws RuntimeException("disk full")
        val viewModel = viewModel()

        viewModel.loadDemoData()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.demoMessage?.contains("Demo load failed") == true)
        assertFalse(viewModel.uiState.value.isDemoBusy)
    }
```

Add the import for `DemoDataSeeder`:

```kotlin
import com.smsexpensetracker.data.demo.DemoDataSeeder
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.settings.SettingsViewModelTest"`
Expected: FAIL — constructor signature mismatch / `demoMessage` and `loadDemoData` don't exist.

- [ ] **Step 3: Implement the ViewModel**

In `SettingsViewModel.kt`:

Add `demoMessage` and `isDemoBusy` to `SettingsUiState`:

```kotlin
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val csvMessage: String? = null,
    val isCsvBusy: Boolean = false,
    val pendingExport: ExportResult? = null,
    val demoMessage: String? = null,
    val isDemoBusy: Boolean = false
)
```

Add `demoDataSeeder` to the constructor:

```kotlin
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val exportCsvUseCase: ExportCsvUseCase,
    private val importCsvUseCase: ImportCsvUseCase,
    private val demoDataSeeder: DemoDataSeeder
) : ViewModel() {
```

Add the import:

```kotlin
import com.smsexpensetracker.data.demo.DemoDataSeeder
```

Add two functions before the closing brace:

```kotlin
    fun loadDemoData() {
        if (_uiState.value.isDemoBusy) return
        _uiState.update { it.copy(isDemoBusy = true) }
        viewModelScope.launch {
            _uiState.update {
                runCatching { demoDataSeeder.seedIfEmpty() }.fold(
                    onSuccess = { inserted ->
                        it.copy(
                            isDemoBusy = false,
                            demoMessage = if (inserted > 0) "Loaded $inserted demo transactions" else "Demo data already loaded"
                        )
                    },
                    onFailure = { e ->
                        it.copy(isDemoBusy = false, demoMessage = "Demo load failed: ${e.message}")
                    }
                )
            }
        }
    }

    fun consumeDemoMessage() {
        _uiState.update { it.copy(demoMessage = null) }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.settings.SettingsViewModelTest"`
Expected: PASS (all existing + 3 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): load demo data on demand"
```

---

### Task 4: SettingsScreen — Load demo data row

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `SettingsViewModel.loadDemoData()`, `SettingsUiState.demoMessage`, `SettingsViewModel.consumeDemoMessage()` (Task 3).
- Produces: nothing (UI-only).

- [ ] **Step 1: Add the import**

In `SettingsScreen.kt`, add `Icons.Filled.PlayArrow` to the material icons imports (alphabetically near the other `Icons.Filled.*` imports):

```kotlin
import androidx.compose.material.icons.filled.PlayArrow
```

- [ ] **Step 2: Add a snackbar LaunchedEffect for the demo message**

Directly below the existing `LaunchedEffect(state.csvMessage)` block (lines 64–69), add:

```kotlin
    LaunchedEffect(state.demoMessage) {
        state.demoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeDemoMessage()
        }
    }
```

- [ ] **Step 3: Add the "Load demo data" row**

In the Data section, after the `SettingsActionRow` for "Import CSV" (line 203–211) and before the "Logs" row, add:

```kotlin
        SettingsActionRow(
            icon = Icons.Filled.PlayArrow,
            label = "Load demo data",
            onClick = { viewModel.loadDemoData() }
        )
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt
git commit -m "feat(settings): add Load demo data action"
```

---

### Task 5: Seed DCB Bank + full 14-rule template set

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/SeedDatabaseCallback.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: On `onCreate`, seeds 6 banks and 14 `sms_rules` rows (ids 1–14). Later tasks verify via emulator + docs. Note: bank ids already in use are HDFC=1, ICICI=2, SBI=3, Axis=4, Pluxee=5; DCB becomes 6.

- [ ] **Step 1: Add the DCB bank**

In `seedBanks`, append to the `banks` list (after the Pluxee entry):

```kotlin
            "DCB Bank" to "DCBANK"
```

- [ ] **Step 2: Rewrite `seedSmsRules`**

Replace the entire `seedSmsRules` body. The `rules` list becomes:

```kotlin
        val rules = listOf(
            "HDFC CC Debit" to 1L to "Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}",
            "HDFC CC UPI Debit" to 1L to "Txn Rs.{amount} On HDFC Bank Card {card} At {description} by UPI {upi} On {date}",
            "HDFC CC Refund" to 1L to "Alert! Rs. {amount} refunded by {description} on {date} & adjusted against HDFC Bank Credit Card {card}",
            "HDFC UPI Credit" to 1L to "Rs.{amount} credited to HDFC Bank A/c {account} on {date} from VPA {description} (UPI",
            "HDFC e-Mandate" to 1L to "INR {amount} deducted from HDFC Bank A/C No {account} towards {description} UMRN",
            "HDFC NetBanking" to 1L to "Rs. {amount} from A/c {account} to {description} via HDFC Bank NetBanking",
            "HDFC NEFT Credit" to 1L to "INR {amount} deposited in HDFC Bank A/c {account} on {date} for NEFT Cr-{description}.Avl bal",
            "ICICI UPI Debit" to 2L to "ICICI Bank Acct {account} debited for Rs {amount} on {date}; {description} credited. UPI",
            "ICICI UPI Credit" to 2L to "Acct {account} is credited with Rs {amount} on {date} from {description}. UPI",
            "ICICI IMPS Credit" to 2L to "ICICI Bank Account {account} is credited with Rs {amount} on {date} by {description}. IMPS",
            "Pluxee Meal Spend" to 5L to "Rs. {amount} spent from Pluxee Meal Card wallet, card no.{card} on {date} at {description}. Avl bal",
            "Pluxee Reversal" to 5L to "Your Pluxee Card xx{card} has been credited with INR {amount} on {date}as {description}.",
            "Pluxee Wallet Load" to 5L to "credited with Rs.{amount} towards{wallet} on {description}. Your",
            "DCB POS/Ecom Debit" to 6L to "INR {amount} debited DCB Bank a/c*{card} POS/Ecom txn to {description} on {date}"
        )
```

Keep the existing `forEachIndexed` insert loop exactly as-is — it assigns ids `index + 1` (1–14) and doubles apostrophes. The loop code:

```kotlin
        rules.forEachIndexed { index, (descBankId, pattern) ->
            val (desc, bankId) = descBankId
            db.execSQL(
                "INSERT INTO sms_rules (id, bankId, pattern, description) VALUES(${index + 1}, $bankId, '${pattern.replace("'", "''")}', '${desc.replace("'", "''")}')"
            )
        }
```

These 14 templates are byte-for-byte the patterns in the "TPL" rows of `RegexParserTest` (lines 99–165). Do not alter them.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Cross-check every template against its real SMS**

Manually verify (or via a quick read of `RegexParserTest`) that all 14 patterns match their SMS and no rule can wrongly match another bank's SMS. The distinct leading literals are: `Spent Rs.` / `Txn Rs.` / `Alert! Rs.` / `Rs.{amount} credited` / `INR ... deducted from HDFC Bank A/C No` / `Rs. {amount} from A/c` / `INR ... deposited in HDFC Bank A/c` (HDFC); `ICICI Bank Acct` / `Acct ` / `ICICI Bank Account` (ICICI); `Rs. {amount} spent from Pluxee` / `Your Pluxee Card xx` / `credited with Rs.` (Pluxee); `INR ... debited DCB Bank a/c*` (DCB). Because the first-match loop is `ORDER BY bankId, description` and every leading literal is unique across banks, ordering cannot cause a wrong match.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/database/SeedDatabaseCallback.kt
git commit -m "feat(seed): add DCB bank and full template rule set"
```

---

### Task 6: Update TESTING.md and TODO.md

**Files:**
- Modify: `TESTING.md`
- Modify: `TODO.md`

**Interfaces:**
- Consumes: the completed changes from Tasks 1–5.
- Produces: accurate QA checklist + task tracker.

- [ ] **Step 1: Read the current docs**

Read `TESTING.md` and `TODO.md` from the repo root to find the exact wording to update.

- [ ] **Step 2: Update TESTING.md**

- In §1 (fresh install / onboarding), remove or strike the note that the app "auto-seeds ~60 demo rows". Replace with: a fresh install now starts with an **empty** transaction list.
- In the settings/QA section (§11 or the settings checklist), add a check row: "Settings → Data → **Load demo data** inserts 60 transactions; tapping again reports 'Demo data already loaded'."
- In the sync section (§5 or wherever parse coverage is described), update the expected outcome of `scripts/push_test_sms.sh`: **all 14 messages now parse** (7 HDFC incl. CC UPI Debit / CC Refund / NetBanking, 3 ICICI incl. IMPS Credit, 3 Pluxee, 1 DCB) with 0 unparsed. Remove any earlier statement that DCB/Pluxee messages are parse failures.
- If TESTING.md lists the seeded banks, add DCB Bank.

- [ ] **Step 3: Update TODO.md**

- Mark the demo-data task as done-in-this-feature: app no longer auto-seeds; demo data is an opt-in Settings action ("Load demo data").
- Under SMS rule management, note that the seed now contains 14 template rules across 6 banks (HDFC 7, ICICI 3, Pluxee 3, DCB 1) covering all `push_test_sms.sh` patterns.

- [ ] **Step 4: Commit**

```bash
git add TESTING.md TODO.md
git commit -m "docs: reflect empty-start behavior and full rule coverage"
```

---

### Task 7: Full verification gate + emulator smoke test

**Files:**
- None modified.

**Interfaces:**
- Consumes: Tasks 1–6.

- [ ] **Step 1: Clean build + full test suite**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 310 tests, 0 failures, 0 errors.

- [ ] **Step 2: Build the APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, APK produced at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Emulator — fresh-install empty state**

On an emulator: uninstall the app, install the debug APK, open it.
Expected: Dashboard/Transactions shows an **empty** list (no demo rows).

- [ ] **Step 4: Emulator — full parse coverage**

Run: `scripts/push_test_sms.sh`, then trigger a sync.
Expected: all 14 SMS parse (0 unparsed); transactions appear under their banks; the 4 messages that previously failed (3 Pluxee + 1 DCB) now succeed.

- [ ] **Step 5: Emulator — Load demo data**

Settings → Data → **Load demo data**.
Expected: snackbar "Loaded 60 demo transactions"; transaction list shows 60 rows. Tap again → snackbar "Demo data already loaded".

- [ ] **Step 6: Emulator — Rule Editor shows all 14 seeded rules**

Settings → Banks & Rules → DCB Bank, HDFC Bank, ICICI Bank, Pluxee.
Expected: DCB Bank has 1 rule "DCB POS/Ecom Debit"; HDFC has 7; ICICI has 3; Pluxee has 3 — all shown as editable templates (Pattern field uses template syntax, not regex).

- [ ] **Step 6: Emulator — Rule Editor shows all 14 seeded rules**

Settings → Banks & Rules → DCB Bank, HDFC Bank, ICICI Bank, Pluxee.
Expected: DCB Bank has 1 rule "DCB POS/Ecom Debit"; HDFC has 7; ICICI has 3; Pluxee has 3 — all shown as editable templates (Pattern field uses template syntax, not regex).

- [ ] **Step 7: Confirm no stray files staged**

Run: `git status --short`
Expected: only the feature's commits; `DashboardViewModel.kt`, `opencode.json`, and any `*.db` remain uncommitted as before.

- [ ] **Step 8: Final commit if anything changed during verification**

If any doc or code tweak was needed during verification, commit it with a descriptive message (e.g. `fix: ...`). If nothing changed, skip — Tasks 1–6 already committed everything.
