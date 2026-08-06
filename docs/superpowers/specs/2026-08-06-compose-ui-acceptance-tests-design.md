# Compose UI Smoke Acceptance Tests

> **Status:** Design (brainstormed + approved, pending implementation plan)

## Goal

Turn the manual QA checklist in `TESTING.md` into an automated, headless, repeatable
Compose UI smoke suite that runs on an emulator. A new-user onboarding feature
(DataStore-backed first-launch gate + Dashboard "Get started" card) landed recently;
this suite is the acceptance layer that proves it — and the rest of the main screens —
still work as the app evolves.

## Success Criteria

1. `./gradlew pixel9Api35DebugAndroidTest` provisions a headless emulator and runs
   the whole suite green, with zero manual steps.
2. `./gradlew connectedDebugAndroidTest` still works against an already-running emulator.
3. Every manual QA bullet in `TESTING.md` §1 (First Launch) and §2 (Dashboard) has an
   automated equivalent; the other main screens each have a render + key-interaction smoke test.
4. Tests are deterministic: fresh state per test, no hard sleeps, `waitUntil` for async.

## Scope

- **In scope:** onboarding flow, Dashboard (incl. Get-started card), Transactions,
  Settings, Categorize, Parser, bottom-nav navigation. Real Room DB + DataStore
  (no fakes — this is acceptance against real persistence).
- **Out of scope:** SMS-permission *system dialog UI* (grant/deny are pre-set via
  instrumentation and the app's *post-permission* behavior is asserted, not the dialog
  itself). Android instrumentation runs via Compose rule, not `adb` scripting.

## Non-Goals

- No new runtime dependencies in the app.
- No test-target application (no `HiltTestApplication` / DI fakes in this pass).
- No unit-test rewrites; the 407 JVM tests are untouched.

---

## Architecture

### Test identity: selective `testTag`s

No test strings leak into production copy. Four structural tags are added to main-source
composables via a small `TestTags` object:

| Tag constant | Attached to | Purpose |
|---|---|---|
| `TestTags.BottomNav` | `PillNavigationBar` root `Row` | assert nav bar hidden (onboarding) / visible (main) |
| `TestTags.GetStartedCard` | `GetStartedCard` root | stable card presence + dismiss assertion |
| `TestTags.EmptyState` | shared `EmptyState` composable | distinguish "no data" screens |
| `TestTags.OnboardingPager` | `HorizontalPager` in `OnboardingScreen` | pager navigation assertions |

File: `app/src/main/java/com/smsexpensetracker/ui/TestTags.kt`

### Shared rule + reset hook

`SmokeTestRule` (androidTest): wraps `createAndroidComposeRule<MainActivity>()` and, in
`@Before`/`@After`, resets state using the instrumentation `targetContext` directly:

- DataStore: delete the `settings` prefs file(s) — resets `onboarding_complete`.
- Room: delete the app DB file — resets transactions/categories/banks.

This yields a true fresh-install state for every test without a test application.

`TestTags`, reset logic, and the permission helper live in
`app/src/androidTest/java/com/smsexpensetracker/util/`, kept separate from
`ExampleInstrumentedTest.kt` (scaffold test — replaced).

### Permission pre-setting

A helper on the shared rule (or a util): `UiAutomation.grantRuntimePermission(pkg, READ_SMS)`
/ `RECEIVE_SMS` for grant-path tests, and `revokeRuntimePermission(...)` for deny-path tests.
Idempotent; only used by tests that touch permission.

### Async discipline

- All waits use `composeTestRule.waitUntil { … }` / `waitForIdle` — no `Thread.sleep`.
- Demo seeding: wait until the Get-started card disappears (or 60 rows render).

---

## Test Classes (per-screen)

| Class | Manual bullet it replaces | Assertions |
|---|---|---|
| `OnboardingAcceptanceTest` | TESTING §1 | fresh-install welcome (pager present, bottom nav absent) → Skip → Dashboard + Get-started card → relaunch (recreate scenario) → welcome does NOT reappear → Try demo data → 60 rows, card gone. Permission grant path (pre-granted → sync proceeds) and deny path (denied → lands on empty Dashboard). |
| `DashboardSmokeTest` | TESTING §2 | empty dashboard renders summary cards + Get-started card; X dismiss hides card (per session); demo data → card gone; View All → Transactions. |
| `TransactionsSmokeTest` | (manual smoke) | empty state renders; "+" FAB → manual entry; search bar present. |
| `SettingsSmokeTest` | (manual smoke) | Settings header + Appearance / Data / About sections render. |
| `CategorizeSmokeTest` | (manual smoke) | screen renders header + primary control. |
| `ParserSmokeTest` | (manual smoke) | screen renders "Parser Test" + input + "Parsed result" area. |
| `NavigationSmokeTest` | (manual smoke) | bottom nav switches across all 5 tabs; each destination renders. |

## Gradle wiring

`app/build.gradle.kts`:

- Add `testOptions { managedDevices { … } }` — one device named `pixel9Api35` (API 35,
  matching the existing `Pixel9Prov15api35` system image, to avoid a download) so the suite
  is fully headless: `./gradlew pixel9Api35DebugAndroidTest`.
- Keep `connectedDebugAndroidTest` functional for the live emulator.
- Only configuration in the Gradle file — no dependency changes.

## Error Handling & Flakiness Rules

- Runtime-permission grant/revoke is idempotent and guarded per-test.
- Tests not touching permission are unaffected by grant/revoke.
- `waitUntil` timeouts use generous bounds (seeding takes a moment); failures report the
  last observed UI state.
- Tests assert only structural/stable content; never the 60 seeded rows' exact values.

---

## Risks / Open Questions

1. **Managed-device API choice** — use API 35 (installed) to avoid a system-image download.
   Compile/target SDK is 37/36, but the tests run fine on API 35.
2. **`rememberSmsSyncPermission` on Dashboard** registers even when the card can't render
   (existing deferred minor) — permission tests must not assume a dialog is absent solely
   because the card is hidden.
3. **Recreate-scenario relaunch test** — `activityRule.scenario.recreate()` is the cleanest
   way to simulate relaunch; if it proves flaky on the managed device, fall back to asserting
   the DataStore flag value directly.

## Testing

- The suite itself is verified by running it (managed device + live emulator).
- The 407 JVM unit tests must stay green — the only main-source change is `TestTags.kt`
  (additive, no behavior change).
