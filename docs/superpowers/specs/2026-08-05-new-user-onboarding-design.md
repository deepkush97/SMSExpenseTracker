# New-User Onboarding — Design

**Date:** 2026-08-05
**Status:** Approved by user (2026-08-05)

## 1. Goal

Give a brand-new user a guided first-launch experience that explains what the app does, how SMS data is handled (privacy), the two ways to start (demo data or real SMS sync), and what the Dashboard shows. A Dashboard "Get started" card stays as the safety net for users who skip the welcome flow.

This closes two open TODOs:
- **F5. Dashboard empty state** (`TODO.md` line 20, 132) — Dashboard has no empty state today; it renders zero summaries with no guidance.
- **F5 first-launch detection** (`TODO.md` line 193) — "Implement first-launch detection (`SharedPreferences` flag)".

## 2. Scope

- **In:** first-launch gate in `MainActivity`; `OnboardingPreferences` (DataStore); 3-page `OnboardingFlow` (HorizontalPager); `OnboardingActionsViewModel` (demo load, sync, barrier, mark-complete); shared SMS-permission helper extracted from `TransactionsScreen`; Dashboard "Get started" card; unit tests; `TESTING.md` + `TODO.md` updates.
- **Out:** no changes to the NavGraph, demo seeder/generator, sync pipeline, parser, DI modules, or the Transactions/Settings screens' behavior (beyond the permission-helper refactor). No Room schema change. No migration.

## 3. Design decisions

- **Gate above the NavGraph.** `MainActivity` switches at the root between `OnboardingFlow` and the existing Scaffold + `AppNavHost`. The bottom navigation bar is hidden automatically because onboarding is outside the nav graph. `startDestination` stays `BottomNavItem.Dashboard.route`.
- **One-time flag, never again** (user decision). Onboarding is shown only until the user completes or skips it. Skipping still marks the flag done; the Dashboard card is the persistent safety net.
- **Act immediately** (user decision). "Try with demo data" seeds and lands on a populated Dashboard. "Sync my SMS" runs the permission + sync flow, then lands on the Dashboard. Demo load / sync run in the Activity-scoped `OnboardingActionsViewModel`, so they survive the switch to the main UI and the Dashboard populates via its existing Flows.
- **Keep the existing demo-data gate** (user decision). If real sync is attempted while demo data is loaded, the existing `DemoDataBarrierDialog` flow is reused. Onboarding text notes demo data can be deleted from Settings.
- **Concise 2–3 screen tour** (user decision): (1) what the app does + privacy, (2) two ways to start, (3) Dashboard tour with text + bullet callouts (no visual mockup, so it can't drift from the real UI).

## 4. Components

### 4.1 `OnboardingPreferences` (new — `core/settings/OnboardingPreferences.kt`)

Mirrors `DemoDataPreferences` / `ThemePreferences`:

- Uses the same `settings` DataStore (`di/SettingsModule.kt`).
- `val onboardingComplete: Flow<Boolean>` — key `onboarding_complete`, default `false`.
- `suspend fun setOnboardingComplete(complete: Boolean)`.

### 4.2 `OnboardingActionsViewModel` (new — `ui/onboarding/OnboardingActionsViewModel.kt`)

`@HiltViewModel`. Injects `DemoDataSeeder`, `SmsSyncUseCase`, `DemoDataPreferences`, `OnboardingPreferences`. Self-contained — used by both `OnboardingScreen` and the Dashboard card (instance identity below).

State + behavior:

| member | behavior |
|---|---|
| `uiState: StateFlow<OnboardingActionsUiState>` | `isBusy: Boolean`, `showDemoBarrier: Boolean`, `demoLoaded: Boolean` |
| `loadDemoData()` | if busy, return; run `demoDataSeeder.seedIfEmpty()` off main; then `onboardingPreferences.setOnboardingComplete(true)`; clear busy |
| `sync()` | if busy, return; if demo loaded → `showDemoBarrier = true`, return; run `smsSyncUseCase.sync()` off main; then `setOnboardingComplete(true)` |
| `markComplete()` | `setOnboardingComplete(true)` (used by Skip and by the permission-deny path) |
| `confirmDeleteDemoData()` / `dismissDemoBarrier()` | same shape as `TransactionsViewModel` (reuse `DemoDataBarrierDialog`) |
| `isBusy` | drives a progress state on the onboarding buttons / Dashboard card |

Notes:
- Permission flow is NOT owned by the ViewModel — it stays in the composable layer via the shared helper (§4.4), mirroring the current `TransactionsScreen` pattern.
- `setOnboardingComplete(true)` flips the `MainActivity` gate to the main UI; the Activity-scoped ViewModel keeps running the seed/sync that was already launched, and the Dashboard's Flows pick up the new rows.
- Instance identity: when created from `OnboardingScreen` (outside the NavGraph, `LocalViewModelStoreOwner` = the Activity) the VM is Activity-scoped and survives the switch. When created from the Dashboard card (inside the NavHost) it is scoped to the Dashboard backstack entry — a distinct instance. This is acceptable because onboarding and the Dashboard card never coexist, and each instance is self-contained (`loadDemoData` / `sync` / `markComplete` have no cross-session state).

### 4.3 `OnboardingScreen` (new — `ui/onboarding/OnboardingScreen.kt`)

- 3-page `HorizontalPager` (no swipe lockout; swipe + Next both advance), `PageIndicator` dots, `Skip` button top-right on pages 1–2 and a `Skip` / "Not now" affordance on page 3.
- **Page 1 — Understand SMS:** app turns bank SMS into categorized transactions; privacy bullets: data stays on-device, no account, no internet needed.
- **Page 2 — Choose your start:** two paths explained (demo data = explore safely with 60 sample transactions, deletable from Settings; sync = connect real bank SMS, once granted new bank SMS are captured automatically). Note the auto-capture (F11).
- **Page 3 — Your Dashboard:** text + bullet callouts describing Total Spent/Received cards, bank bar chart, monthly trend, category pie, recent transactions; then two primary actions:
  - **Try with demo data** → `viewModel.loadDemoData()`
  - **Sync my SMS** → shared permission helper: granted → `viewModel.sync()`; denied → `viewModel.markComplete()` (user lands on empty Dashboard with the card).
  - While `isBusy`, show a `CircularProgressIndicator` in place of the action buttons.

### 4.4 Shared SMS-permission helper (new — `ui/components/SmsSyncPermission.kt`)

Extract the permission wiring currently inline in `TransactionsScreen.kt:83-114` into a reusable composable:

- Owns `PermissionManager`, the `rememberLauncherForActivityResult(RequestMultiplePermissions)` launcher, and the `showRationale` state.
- Exposes a `requestSync: () -> Unit` lambda to the caller: if permission already granted → caller's sync lambda runs; if rationale needed → show rationale `AlertDialog` (text: "SMS Expense Tracker reads your bank SMS to extract transaction details. SMS data stays on your device."), then launch; else launch directly.
- On result: all granted → caller's sync lambda; else → caller's deny lambda (snackbar "SMS access is needed to sync transactions" + "Open Settings" fallback).

Refactor `TransactionsScreen` to use it. Onboarding's "Sync my SMS" and the Dashboard card use the same helper, so permission behavior stays in one place.

### 4.5 MainActivity gate (`MainActivity.kt`)

- `@AndroidEntryPoint` Activity gains field injection: `@Inject lateinit var onboardingPreferences: OnboardingPreferences`.
- Collect `onboardingComplete` (`.collectAsState(initial = null)`); when `null` show blank; when `false` show `OnboardingScreen()`; when `true` show the existing `Scaffold` + `AppNavHost`.

### 4.6 Dashboard "Get started" card (`DashboardScreen.kt`)

- When `!state.isLoading && state.recentTransactions.isEmpty()`, render a `Card` as the first item in the `LazyColumn` (before/above the summary cards):
  - Title: "Get started"
  - One-liner: "Load sample data to explore, or sync your real bank SMS."
  - Buttons: **Try demo data** (`onboardingViewModel.loadDemoData()`), **Sync SMS** (shared permission helper → `sync()`), dismiss X (sets a `remember { }` local flag to hide for the session).
- Card is a safety net only — it disappears as soon as any transaction exists.
- `DashboardScreen` gains an optional `onboardingViewModel: OnboardingActionsViewModel = hiltViewModel()` (scoped to the Dashboard backstack entry; see §4.2 note on instance identity).

## 5. Behavior changes

- Fresh install: first launch shows the welcome flow. After any exit path (path chosen or skipped) the user lands on the Dashboard — populated if demo data was loaded / sync inserted rows, otherwise empty with the "Get started" card.
- Existing installs: flag defaults `false`, so upgrading users see the welcome once. (Single-line flip: default key could be seeded `true` for existing installs — see §7 decision.)
- `TransactionsScreen`: no visual/behavior change; only the permission wiring is moved into the shared helper.

## 6. Error handling

- Demo load failure: `seedIfEmpty()` guarded by `count() == 0`; failures surface as `isBusy = false` and a snackbar-style message from the ViewModel (mirror `SettingsViewModel.loadDemoData` messaging).
- Sync failure: existing `SmsSyncUseCase.sync()` result handled; message surfaced (mirror `TransactionsViewModel.sync` — "Scanned X, added Y, unparsed Z" / "Sync failed. Try again.").
- Permission denied: user lands on the Dashboard with the card; retry is available there and in Transactions.

## 7. Known risks / decisions

- **Existing-install flag default.** First-launch detection uses a DataStore boolean defaulting `false`. Upgrading users would see onboarding once. Decision: acceptable — treat the welcome as a general "what's new"/getting-started pass. (No data migration exists for prefs.)
- **Activity-scoped `OnboardingActionsViewModel`** keeps work alive across the onboarding→app switch; its coroutines outlive the onboarding composable. This is the intended design, but it means the VM is not cleared until the Activity finishes.
- **Per-session card dismissal** (`remember`, not persisted). Revisit if the user wants a permanent dismiss.
- **No Room / DB changes.** Card visibility derives from existing `recentTransactions` state.

## 8. Testing

- `OnboardingPreferencesTest` (new): mirrors `DemoDataPreferencesTest` (real `PreferenceDataStoreFactory` in-memory DataStore) — default false, set/read round-trip.
- `OnboardingActionsViewModelTest` (new, MockK): 
  - `loadDemoData()` calls `seedIfEmpty()` and sets onboarding complete.
  - `sync()` calls `SmsSyncUseCase.sync()` and sets onboarding complete.
  - `sync()` with demo data loaded shows the barrier and does NOT call the use case.
  - `markComplete()` sets onboarding complete.
- Existing tests must stay green; `TransactionsScreen` behavior is covered by existing UI/VM tests where present.
- `TESTING.md`: update §1/§11 to describe first-launch onboarding and the Dashboard card.
- `TODO.md`: mark F5 (Dashboard empty state) and first-launch detection done.
- Gate: `./gradlew testDebugUnitTest assembleDebug`.

## 9. Acceptance criteria

1. Fresh install launches into the 3-page welcome flow; bottom nav bar is hidden.
2. "Try with demo data" → Dashboard populates with 60 rows; welcome does not reappear on relaunch.
3. "Sync my SMS" → permission flow runs; on grant, sync runs and Dashboard populates; on deny, user lands on empty Dashboard; welcome does not reappear on relaunch.
4. Skip → empty Dashboard with "Get started" card; card has both actions + dismiss; welcome does not reappear on relaunch.
5. Dashboard "Get started" card disappears once any transaction exists.
6. `./gradlew testDebugUnitTest assembleDebug` green.
