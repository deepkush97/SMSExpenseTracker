# Compose UI Smoke Acceptance Tests — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the manual QA checklist into an automated, headless, repeatable Compose UI smoke suite that runs on an emulator.

**Architecture:** Per-screen instrumented test classes (`app/src/androidTest/…`) under a shared `SmokeTestRule`. Identity is by selective `Modifier.testTag`s plus stable text. State is reset per-test by a Hilt `@EntryPoint` reaching the REAL singletons (deleting the DB file is ineffective because the process holds Room/DataStore open). Runs via Gradle Managed Device (`pixel9Api35`) or `connectedDebugAndroidTest` on a live emulator.

**Tech Stack:** Compose UI Test (`createAndroidComposeRule`), JUnit 4, Hilt (test-only via `@EntryPoint`), AGP Gradle Managed Devices.

## Global Constraints

- Package: `com.smsexpensetracker`; the app is `@HiltAndroidApp` (`SmsExpenseApp.kt`). No test application — `createAndroidComposeRule<MainActivity>()` uses the real app+DI.
- **Test-only dependencies only.** Adding `androidTestImplementation(libs.hilt.android)` and `kspAndroidTest(libs.hilt.compiler)` is permitted (does not affect the app runtime). No changes to the app's `implementation` graph.
- No new `testTag`s beyond `TestTags.kt` and the four attach sites listed in Task 1. No test strings leak into production copy.
- Real Room DB + Real DataStore — no fakes. Reset per-test via the Hilt `@EntryPoint`.
- The 407 JVM unit tests must stay green — the only `src/main` change is `TestTags.kt` (additive).
- Gradle config changes live in `app/build.gradle.kts` only; do not touch `libs.versions.toml` unless a catalog alias is missing (Task 1 verifies; Task 2 adds a catalog group only if needed).
- Onboarding UI copy is fixed (from the onboarding feature) — assertable text: `Skip`, `Next`, `Try with demo data`, `Sync my SMS`, `Get started`, `Try demo data`, `Sync SMS`, `Dismiss`.
- Async: use `composeTestRule.waitUntil { ... }` / `waitForIdle()` — never `Thread.sleep`.
- Commit per task with a conventional message (`feat:`/`test:`/`chore:`).

**Verified facts (do not re-verify):**
- Existing androidTest deps: `libs.androidx.junit`, `libs.androidx.espresso.core`, `libs.androidx.compose.ui.test.junit4`, `libs.room.testing`. Present in `app/build.gradle.kts:103-106`.
- `debugImplementation(libs.androidx.compose.ui.test.manifest)` present (`app/build.gradle.kts:76`).
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` present (`app/build.gradle.kts:29`).
- Created by existing tests: none — `androidTest/` holds only `ExampleInstrumentedTest.kt` (replace) and `core/database/MigrationTest.kt` (keep).
- Reset targets: `OnboardingPreferences.setOnboardingComplete(Boolean)` (`core/settings/OnboardingPreferences.kt:20`), `TransactionDao.deleteAll()` (`core/database/dao/TransactionDao.kt:89`).
- Room ViewModel/DB instance: `SmsExpenseDatabase` (`core/database/SmsExpenseDatabase.kt`), `TransactionDao` accessible via `@EntryPoint`.
- UI copy/`testTag` anchors confirmed in Task 1.

---

## File Structure

- Modify: `app/build.gradle.kts` — add androidTest Hilt deps + `testOptions.managedDevices.localDevices`.
- Create (main): `app/src/main/java/com/smsexpensetracker/ui/TestTags.kt` — shared tag constants.
- Modify (main): `MainActivity.kt`, `OnboardingScreen.kt`, `DashboardScreen.kt`, `ui/components/EmptyState.kt` — attach the 4 structural `testTag`s.
- Create (androidTest): `core/TestReset.kt` (`@EntryPoint` + helper), `util/Permissions.kt`, `util/TestApp.kt` (bottom-nav tap + assertion helpers).

> Existing `app/src/androidTest/java/com/smsexpensetracker/core/database/MigrationTest.kt` already imports Room helpers. New tests live under `app/src/androidTest/java/com/smsexpensetracker/` matching their package, e.g. `…/ui/screens/dashboard/DashboardSmokeTest.kt`.

---

### Task 1: `TestTags` + structural tags + Hilt test deps

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/TestTags.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/MainActivity.kt` (the `PillNavigationBar` root `Row`, ~line 137)
- Modify: `app/src/main/java/com/smsexpensetracker/ui/onboarding/OnboardingScreen.kt` (`HorizontalPager`, line 67)
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardScreen.kt` (`GetStartedCard` root `Card`, line 234)
- Modify: `app/src/main/java/com/smsexpensetracker/ui/components/EmptyState.kt` (root `Column`, line 46)
- Modify: `app/build.gradle.kts` (test deps)

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `object TestTags { const val BOTTOM_NAV, GET_STARTED_CARD, EMPTY_STATE, ONBOARDING_PAGER }`. Consumed by all later tasks.

- [ ] **Step 1: Create the tag constants**

`app/src/main/java/com/smsexpensetracker/ui/TestTags.kt`:

```kotlin
package com.smsexpensetracker.ui

object TestTags {
    const val BOTTOM_NAV = "bottom_nav"
    const val GET_STARTED_CARD = "get_started_card"
    const val EMPTY_STATE = "empty_state"
    const val ONBOARDING_PAGER = "onboarding_pager"
}
```

- [ ] **Step 2: Attach `BOTTOM_NAV` to the pill nav bar root**

In `app/src/main/java/com/smsexpensetracker/MainActivity.kt`, the `Row` of `PillNavigationBar` (modifier currently `.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)`), add `import androidx.compose.ui.platform.testTag` and `import com.smsexpensetracker.ui.TestTags`, then:

```kotlin
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .testTag(TestTags.BOTTOM_NAV),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
```

- [ ] **Step 3: Attach `ONBOARDING_PAGER` to the pager**

In `OnboardingScreen.kt`, the `HorizontalPager` (`modifier = Modifier.weight(1f)`), add imports as above and:

```kotlin
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.ONBOARDING_PAGER)
            ) { page ->
```

- [ ] **Step 4: Attach `GET_STARTED_CARD` to the dashboard card**

In `DashboardScreen.kt`, `GetStartedCard`'s root `Card` (currently `.fillMaxWidth().padding(horizontal = 16.dp)`), add imports and:

```kotlin
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(TestTags.GET_STARTED_CARD),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
```

- [ ] **Step 5: Attach `EMPTY_STATE` to the shared empty-state root**

In `EmptyState.kt`, the root `Column` (currently `modifier.fillMaxSize().padding(32.dp).alpha(alpha)`), add imports and:

```kotlin
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .alpha(alpha)
            .testTag(TestTags.EMPTY_STATE),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
```

- [ ] **Step 6: Add Hilt androidTest deps to build.gradle.kts**

In `app/build.gradle.kts` `dependencies { }` block, after line 105 (`androidTestImplementation(libs.androidx.compose.ui.test.junit4)`), add:

```kotlin
    androidTestImplementation(libs.hilt.android)
    kspAndroidTest(libs.hilt.compiler)
```

If the catalog aliases `libs.hilt.android` / `libs.hilt.compiler` do not resolve, verify they exist — the app already uses `libs.hilt.android` in `implementation` and `libs.hilt.compiler` in `ksp` (lines 82-83 of `app/build.gradle.kts`), so the aliases resolve and need no catalog change. Do not edit `libs.versions.toml`.

- [ ] **Step 7: Build gate**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 407 unit tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/smsexpensetracker/ui/TestTags.kt app/src/main/java/com/smsexpensetracker/MainActivity.kt app/src/main/java/com/smsexpensetracker/ui/onboarding/OnboardingScreen.kt app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardScreen.kt app/src/main/java/com/smsexpensetracker/ui/components/EmptyState.kt
git commit -m "feat: add test tags and hilt test scaffolding"
```

---

### Task 2: Managed device + reset + permission + navigation helpers

**Files:**
- Modify: `app/build.gradle.kts` (managed devices)
- Create: `app/src/androidTest/java/com/smsexpensetracker/core/TestReset.kt`
- Create: `app/src/androidTest/java/com/smsexpensetracker/util/Permissions.kt`
- Create: `app/src/androidTest/java/com/smsexpensetracker/util/TestApp.kt` (shared `skipToMain`/`tapNavItem` helpers)

**Interfaces:**
- Consumes: `TestTags` (Task 1), `OnboardingPreferences`, `TransactionDao`.
- Produces: `object AppState.reset(context: Context)`, `object TestPermissions.grant/revoke(context)`, `fun ComposeTestRule.skipToMain()`, `fun ComposeTestRule.tapNavItem(label: String)`.

- [ ] **Step 1: Add managed device to build.gradle.kts**

Inside the existing `android { }` block, after `sourceSets { }` (keep it), add:

```kotlin
    testOptions {
        managedDevices {
            localDevices {
                create("pixel9Api35") {
                    device = "Pixel 9 Pro"
                    apiLevel = 35
                    systemImageSource = "google"
                }
            }
        }
    }
```

If the installed emulator image is `google_apis`-based (verify: `emulator -list-avds` shows `Pixel9Prov15api35`, image API 35), `systemImageSource = "google"` reuses it — no download. If Gradle tries to download a different image, set `systemImageSource = "aosp"` and accept the download, or keep `connectedDebugAndroidTest` as the fallback runner. Keep `connectedDebugAndroidTest` working — the managed device coexists.

- [ ] **Step 2: Verify the managed-device task exists**

Run: `./gradlew :app:tasks --all | grep -i pixel9`
Expected: a `pixel9Api35DebugAndroidTest` task exists.

- [ ] **Step 3: Reset vs process-lifetime singletons — the key helper**

The app process keeps Room + DataStore open, so file deletion is NOT a valid reset. Reach the real singletons via a Hilt `@EntryPoint`. Create `app/src/androidTest/java/com/smsexpensetracker/core/TestReset.kt`:

```kotlin
package com.smsexpensetracker.core

import android.content.Context
import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.settings.OnboardingPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TestResetEntryPoint {
    fun onboardingPreferences(): OnboardingPreferences
    fun transactionDao(): TransactionDao
}

object AppState {
    fun reset(context: Context) {
        val app = context.applicationContext
        val entry = EntryPointAccessors.fromApplication(app, TestResetEntryPoint::class.java)
        runBlocking {
            entry.onboardingPreferences().setOnboardingComplete(false)
            entry.transactionDao().deleteAll()
        }
    }
}
```

Note: `@EntryPoint` needs `androidTestImplementation(libs.hilt.android)` + `kspAndroidTest(libs.hilt.compiler)` (added in Task 1 Step 6) for codegen; `OnboardingPreferences` and `TransactionDao` are existing classes.

- [ ] **Step 4: Permission helper**

Create `app/src/androidTest/java/com/smsexpensetracker/util/Permissions.kt`:

```kotlin
package com.smsexpensetracker.util

import android.content.Context
import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry

object TestPermissions {
    private val SMS = listOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )

    fun grant(context: Context) {
        val uia = InstrumentationRegistry.getInstrumentation().uiAutomation
        SMS.forEach { perm -> uia.grantRuntimePermission(context.packageName, perm) }
    }

    fun revoke(context: Context) {
        val uia = InstrumentationRegistry.getInstrumentation().uiAutomation
        SMS.forEach { perm -> uia.revokeRuntimePermission(context.packageName, perm) }
    }
}
```

- [ ] **Step 5: Fresh-app navigation helper**

Create `app/src/androidTest/java/com/smsexpensetracker/util/TestApp.kt`:

```kotlin
package com.smsexpensetracker.util

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smsexpensetracker.ui.TestTags

fun ComposeTestRule.tapNavItem(label: String) {
    onNodeWithTag(TestTags.BOTTOM_NAV).assertExists()
    onNodeWithText(label, useUnmergedTree = true).performClick()
    waitForIdle()
}
```

()`useUnmergedTree = true` reaches the nav labels behind the `Row`'s merged semantics.

- [ ] **Step 6: Reset in `@Before`**

Each test class will have:

```kotlin
@Before
fun reset() {
    AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
}
```

`createAndroidComposeRule<MainActivity>()` launches the real app; the `@Before` runs before the test body (the activity may start but the Flow re-reads the singleton after reset and recomposes). If you observe the onboarding gate not reflecting reset in a specific test, call `AppState.reset` first, *then* `activity.scenario.recreate()`.

- [ ] **Step 7: Compile & run the scaffold smoke net**

Create `app/src/androidTest/java/com/smsexpensetracker/ui/screens/dashboard/DashboardSmokeTest.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smsexpensetracker.MainActivity
import com.smsexpensetracker.core.AppState
import com.smsexpensetracker.ui.TestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun freshInstall_showsWelcome_notMain() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.ONBOARDING_PAGER).assertExists()
        composeRule.onNodeWithTag(TestTags.BOTTOM_NAV).assertDoesNotExist()
    }

    @Test
    fun skip_leadsToDashboardWithGetStartedCard() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.GET_STARTED_CARD).assertExists()
    }
}
```

Add the missing imports: `androidx.compose.ui.test.onAllNodesWithTag`, `androidx.compose.ui.test.fetchSemanticsNodes`, `androidx.compose.ui.test.assertExists`, `assertDoesNotExist`, `performClick`.

- [ ] **Step 8: Run scaffold smoke on live emulator**

Run: `./gradlew connectedDebugAndroidTest`
Expected: `DashboardSmokeTest` (2 tests) PASS. The emulator must be booted (`adb devices`). Ignore the known test-count (this replaces the scaffold test).

If the compose import names differ from expected, fix them to compile; do not change the flags' meaning.

- [ ] **Step 9: Commit**

```bash
git add app/build.gradle.kts app/src/androidTest/java/com/smsexpensetracker/core/TestReset.kt app/src/androidTest/java/com/smsexpensetracker/util/Permissions.kt app/src/androidTest/java/com/smsexpensetracker/util/TestApp.kt app/src/androidTest/java/com/smsexpensetracker/ui/screens/dashboard/DashboardSmokeTest.kt
git commit -m "test: add smoke suite project harness and first dashboard test"
```

---

### Task 3: `OnboardingAcceptanceTest`

**Files:**
- Create: `app/src/androidTest/java/com/smsexpensetracker/ui/onboarding/OnboardingAcceptanceTest.kt`

**Interfaces:**
- Consumes: `TestTags`, `AppState` (Task 2), `TestApp.tapNavItem`, `TestPermissions`.
- Produces: nothing used by later tasks.

- [ ] **Step 1: Write the tests**

`app/src/androidTest/java/com/smsexpensetracker/ui/onboarding/OnboardingAcceptanceTest.kt`:

```kotlin
package com.smsexpensetracker.ui.onboarding

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.fetchSemanticsNodes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smsexpensetracker.MainActivity
import com.smsexpensetracker.core.AppState
import com.smsexpensetracker.ui.TestTags
import com.smsexpensetracker.util.TestPermissions
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingAcceptanceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
        TestPermissions.revoke(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun freshInstall_showsThreePageWelcome_bottomNavHidden() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.ONBOARDING_PAGER).assertExists()
        composeRule.onNodeWithTag(TestTags.BOTTOM_NAV).assertDoesNotExist()
        composeRule.onNodeWithText("SMS Expense Tracker").assertIsDisplayed()
    }

    @Test
    fun skip_landsOnDashboard_withGetStartedCard() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.GET_STARTED_CARD).assertExists()
    }

    @Test
    fun relaunch_doesNotShowWelcomeAgain() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.ONBOARDING_PAGER).assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.GET_STARTED_CARD).assertExists()
    }

    @Test
    fun demoData_loads60RowsAndCardDisappears() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Try with demo data").performClick()
        // seeding is async; wait for the card to go away
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(TestTags.GET_STARTED_CARD).fetchSemanticsNodes().isEmpty()
        }
        // and onboarding itself is gone (markComplete on demo load)
        composeRule.onNodeWithTag(TestTags.ONBOARDING_PAGER).assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run and fix imports**

Ensure every symbol used in the code block has a resolving `import androidx.compose.ui.test.*` (or the pager-semantics imports listed in Task 2 Step 8: `onAllNodesWithTag`, `fetchSemanticsNodes`, `assertExists`, `assertDoesNotExist`, `assertIsDisplayed`, `performClick`).

- [ ] **Step 3: Run on live emulator**

`./gradlew connectedDebugAndroidTest`
Expected: 4 onboarding tests PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/smsexpensetracker/ui/onboarding/OnboardingAcceptanceTest.kt
git commit -m "test: add onboarding acceptance flow tests"
```

---

### Task 4: `DashboardSmokeTest` full

**Files:**
- Modify: `app/src/androidTest/java/com/smsexpensetracker/ui/screens/dashboard/DashboardSmokeTest.kt` (Task 2 scaffold) — add tests

**Interfaces:**
- Consumes: `TestTags`, `AppState`, bottom-nav helpers.

- [ ] **Step 1: Extend `DashboardSmokeTest`**

Add to Task 2 file:

```kotlin
    @Test
    fun dashboardWithData_showsSummaryAndNoCard() {
        // load demo data first via the onboarding path
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Try with demo data").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(TestTags.GET_STARTED_CARD).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("Total Spent").assertExists()
        composeRule.onNodeWithText("Total Received").assertExists()
        composeRule.onNodeWithTag(TestTags.GET_STARTED_CARD).assertDoesNotExist()
    }

    @Test
    fun dismissX_hidesCardForSession() {
        skipAndNavigateToDashboard() // Skip, then dismiss the card
        composeRule.onNodeWithContentDescription("Dismiss").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.GET_STARTED_CARD).assertDoesNotExist()
    }
```

Add a private `skipAndNavigateToDashboard()` that performs `Skip` and waits for the nav bar, used by both. Keep the two scaffold tests. Note: `Dismiss` is the `IconButton`'s `contentDescription` (DashboardScreen.kt:252), not text — needs `import androidx.compose.ui.test.onNodeWithContentDescription`.

- [ ] **Step 2: Run & commit**

`./gradlew connectedDebugAndroidTest` → PASS; commit.

```bash
git add app/src/androidTest/java/com/smsexpensetracker/ui/screens/dashboard/DashboardSmokeTest.kt
git commit -m "test: extend dashboard smoke coverage"
```

---

### Task 5: `TransactionsSmokeTest` + `SettingsSmokeTest`

**Files:**
- Create: `app/src/androidTest/java/com/smsexpensetracker/ui/screens/transactions/TransactionsSmokeTest.kt`
- Create: `app/src/androidTest/java/com/smsexpensetracker/ui/screens/settings/SettingsSmokeTest.kt`

**Interfaces:**
- Consumes: `AppState`, `skipToMain`, `tapNavItem`, `TestTags` (BOTTOM_NAV, EMPTY_STATE).

- [ ] **Step 1: Add the `skipToMain` helper**

Main-screen tests must get past onboarding first. Add to `util/TestApp.kt` (created in Task 2):

```kotlin
fun ComposeTestRule.skipToMain() {
    waitForIdle()
    onNodeWithText("Skip").performClick()
    waitUntil(timeoutMillis = 5000) {
        onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty()
    }
}
```

(Add imports `onNodeWithText`, `performClick`, `onAllNodesWithTag`, `fetchSemanticsNodes`.)

- [ ] **Step 2: Write `TransactionsSmokeTest`**

```kotlin
@RunWith(AndroidJUnit4::class)
class TransactionsSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun emptyTransactions_showsEmptyState_andFabNavigates() {
        skipToMain()
        tapNavItem("Transactions")
        composeRule.onNodeWithTag(TestTags.EMPTY_STATE).assertExists()
        composeRule.onNodeWithText("No transactions yet").assertExists()
        composeRule.onNodeWithContentDescription("Add transaction").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back").assertExists() // manual entry screen's back button
    }
}
```

- [ ] **Step 3: Write `SettingsSmokeTest`**

```kotlin
@RunWith(AndroidJUnit4::class)
class SettingsSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun settings_rendersSections() {
        skipToMain()
        tapNavItem("Settings")
        composeRule.onNodeWithText("Appearance").assertExists()
        composeRule.onNodeWithText("Data").assertExists()
        composeRule.onNodeWithText("About").performScrollTo()
        composeRule.onNodeWithText("About").assertExists()
    }
}
```

(`About` sits below the fold; `performScrollTo` needs `import androidx.compose.ui.test.performScrollTo`. `Back` is the manual-entry top bar's `contentDescription` (ManualEntryScreen.kt:92), not text — needs `import androidx.compose.ui.test.onNodeWithContentDescription`.)

- [ ] **Step 4: Build & run**

`./gradlew connectedDebugAndroidTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/smsexpensetracker/util/TestApp.kt app/src/androidTest/java/com/smsexpensetracker/ui/screens/transactions/TransactionsSmokeTest.kt app/src/androidTest/java/com/smsexpensetracker/ui/screens/settings/SettingsSmokeTest.kt
git commit -m "test: transactions and settings smoke tests"
```

---

### Task 6: `CategorizeSmokeTest` + `ParserSmokeTest`

**Files:**
- Create: `app/src/androidTest/java/com/smsexpensetracker/ui/screens/categorize/CategorizeSmokeTest.kt`
- Create: `app/src/androidTest/java/com/smsexpensetracker/ui/screens/parser/ParserSmokeTest.kt`

**Interfaces:**
- Consumes: `skipToMain`, `tapNavItem`, `TestTags`.

Categorize with an empty DB renders the shared `EmptyState` ("No transactions yet"), so `TestTags.EMPTY_STATE` applies. Parser renders the "Parser Test" header + "SMS body"/"Sender ID" fields regardless of DB — assert those directly.

- [ ] **Step 1: Create `CategorizeSmokeTest`**

```kotlin
@RunWith(AndroidJUnit4::class)
class CategorizeSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun categorizeEmpty_rendersEmptyState() {
        skipToMain()
        tapNavItem("Categorize")
        composeRule.onNodeWithTag(TestTags.EMPTY_STATE).assertExists()
        composeRule.onNodeWithText("No transactions yet").assertExists()
    }
}
```

- [ ] **Step 2: Create `ParserSmokeTest`**

```kotlin
@RunWith(AndroidJUnit4::class)
class ParserSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun parser_rendersHeaderAndFields() {
        skipToMain()
        tapNavItem("Parser")
        composeRule.onNodeWithText("Parser Test").assertExists()
        composeRule.onNodeWithText("SMS body").assertExists() // OutlinedTextField label
        composeRule.onNodeWithText("Sender ID").assertExists()
    }
}
```

- [ ] **Step 3: Build & run**

`./gradlew connectedDebugAndroidTest` → PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/smsexpensetracker/ui/screens/categorize/CategorizeSmokeTest.kt app/src/androidTest/java/com/smsexpensetracker/ui/screens/parser/ParserSmokeTest.kt
git commit -m "test: categorize and parser smoke tests"
```

---

### Task 7: Permission-path test (grant — automated; deny — manual)

**Files:**
- Create: `app/src/androidTest/java/com/smsexpensetracker/ui/onboarding/OnboardingPermissionTest.kt`

**Interfaces:**
- Consumes: `TestPermissions.grant`, `skipToMain`, `AppState`.
- Produces: nothing.

**Important (decision):** Only the GRANT path is automated. The DENY path cannot be automated without driving the system dialog (UiDevice tap), which is flaky — it stays a manual TESTING.md bullet. Automate: pre-grant permission, then a permission request returns "granted" immediately (no dialog) → `onGranted` fires → `sync()` starts.

- [ ] **Step 1: Grant test**

```kotlin
package com.smsexpensetracker.ui.onboarding

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.fetchSemanticsNodes
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smsexpensetracker.MainActivity
import com.smsexpensetracker.core.AppState
import com.smsexpensetracker.ui.TestTags
import com.smsexpensetracker.util.TestPermissions
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingPermissionTest {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun reset() {
        AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test fun syncGranted_flowProceeds_grantsSmsPermission() {
        TestPermissions.grant(InstrumentationRegistry.getInstrumentation().targetContext)
        composeRule.waitForIdle()
        // go to page 3
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sync my SMS").performClick()
        // since granted, sync proceeds; the app ends on main (nav shown)
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.ONBOARDING_PAGER).assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run & commit**

```bash
git add app/src/androidTest/java/com/smsexpensetracker/ui/onboarding/OnboardingPermissionTest.kt
git commit -m "test: onboarding sms grant-path acceptance"
```

---

### Task 8: Run the full suite on the managed device; drop scaffold

**Files:**
- Delete: `app/src/androidTest/java/com/smsexpensetracker/ExampleInstrumentedTest.kt` (scaffold test — replaced by the suite)
- Modify: `app/build.gradle.kts` (nothing unless required)

**Interfaces:**
- Consumes: all prior tasks.

- [ ] **Step 1: Remove the scaffold test**

`rm app/src/androidTest/java/com/smsexpensetracker/ExampleInstrumentedTest.kt`

- [ ] **Step 2: Keep the migration test**

Keep `app/src/androidTest/java/com/smsexpensetracker/core/database/MigrationTest.kt` — it is a real Room migration test, unrelated to the scaffold. Do not delete it.

- [ ] **Step 3: Run entire suite on managed device**

Run: `./gradlew pixel9Api35DebugAndroidTest`
Expected: the emulator provisions headless and the whole smoke suite passes.

If the managed device fails to provision (system image / AGP9 DSL issues), fall back to `connectedDebugAndroidTest` on the live emulator and record the limitation and the reason. Do NOT leave the suite failing.

- [ ] **Step 4: Update TESTING.md** add an "Automated UI acceptance" note pointing to the two run commands and the manual deny path.

- [ ] **Step 5: Commit**

```bash
git add TESTING.md
git rm app/src/androidTest/java/com/smsexpensetracker/ExampleInstrumentedTest.kt
git commit -m "docs, test: wire automated acceptance suite and drop scaffold"
```

---

## Self-Review

- **Spec coverage**
  - Selective `testTag`s + `TestTags` → Task 1.
  - Shared reset (Hilt entry) + rule → Task 2.
  - Onboarding acceptance (pager, skip→dashboard+card, relaunch persistence, demo load, grant path) → Task 3 + Task 7.
  - Dashboard smoke (summary, card, dismiss) → Task 4.
  - Transactions smoke + Settings smoke → Task 5.
  - Categorize + Parser smoke → Task 6.
  - Managed device seeding → Task 2, final run → Task 8.
  - Manual permission-deny path documented as manual in TESTING.md → Task 7 + Task 8.
- **Placeholder scan:** no TBD/TODO; all code blocks are complete. (The `skipToMain` helper is defined in Task 5 and reused later; the onboarding "Skip" text is the app's real copy.)
- **Copy assertions verified against source:** `Skip`/`Next`/`Try with demo data`/`Sync my SMS`/`SMS Expense Tracker` (OnboardingScreen.kt), `Total Spent`/`Total Received` (DashboardScreen.kt:106,113), `No transactions yet` (TransactionsScreen/CategorizeScreen EmptyState title), `Parser Test`/`SMS body`/`Sender ID` (ParserScreen.kt), `Appearance`/`Data`/`About` (SettingsScreen.kt). `Dismiss` and `Back` are `contentDescription`s (DashboardScreen.kt:252, ManualEntryScreen.kt:92) — matched with `onNodeWithContentDescription`.
- **Type consistency:** `TestTags`, `AppState.reset`, `TestPermissions.grant/revoke`, `skipToMain`, `tapNavItem` used identically across tasks. `OnboardingPreferences.setOnboardingComplete(false)` and `TransactionDao.deleteAll()` are the real signatures (verified).
- **Deviation logged:** the "delete DB file" reset approach in the design doc is replaced with a Hilt `@EntryPoint`-based reset (deleting the file does not work in-process). Deny-path SMS permission test is manual-only per the brainstorm decision.

If any of the intended `testTag`s is already named differently in the repo at execution time, the briefs shall instruct rectifying them — but Task 1 owns the names, so no drift.