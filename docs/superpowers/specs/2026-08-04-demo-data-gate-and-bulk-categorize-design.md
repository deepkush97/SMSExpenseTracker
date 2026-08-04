# Demo-Data Gate + Bulk Categorize — Design

Date: 2026-08-04
Status: Approved (design discussion with user)

## Goal

Two independent features that fix the same underlying pain: demo transactions getting
mixed with real ones, and SMS-parsed transactions arriving "Uncategorized" with no fast
way to fix them.

1. **Demo-data gate:** while demo transactions exist, all real-data entry points
   (SMS sync, manual entry, CSV import) are blocked with a confirm dialog that offers
   to delete the demo data first. Real data can never be merged with demo data.
2. **Bulk-categorize flow:** a new bottom-nav tab walks through transactions one card
   at a time so the user can assign categories quickly with a dropdown picker.

## Context & invariants

- `DemoDataSeeder.seedIfEmpty()` only seeds when `TransactionDao.count() == 0` — demo
  never seeds into a non-empty DB.
- Demo transactions are **not flagged** in the schema, and we are **not** adding a
  column (user chose a Preferences flag, not a Room migration).
- Because seeding only happens into an empty DB **and** real entry is blocked while the
  flag is set, the invariant holds: **`demoDataLoaded == true` ⇒ every transaction row
  is demo data.** Therefore "Delete demo data" can safely `DELETE FROM transactions` —
  it never touches real rows, because real rows can't exist while the flag is set.
- The flag lives in DataStore (same pattern as `ThemePreferences`), not in the DB.

## Feature 1 — Demo-data gate

### 1.1 `DemoDataPreferences` (new, `core/settings/`)

DataStore-backed, mirroring `ThemePreferences`:
- `val demoDataLoaded: Flow<Boolean>` (default `false`)
- `suspend fun setDemoDataLoaded(loaded: Boolean)`

One `DataStore<Preferences>` instance is shared (Hilt provides it; `ThemePreferences`
already receives it).

### 1.2 `DemoDataSeeder` changes (`data/demo/`)

- `seedIfEmpty()`: after a successful insert (`inserted > 0`), call
  `demoDataPreferences.setDemoDataLoaded(true)`.
- New `suspend fun deleteDemoData()`: `transactionDao.deleteAll()` then
  `setDemoDataLoaded(false)`.
- `DemoDataSeeder` gains a `TransactionDao.deleteAll()` dependency:
  new `@Query("DELETE FROM transactions") suspend fun deleteAll()` in `TransactionDao`.
- Parse logs are **not** touched: demo seeding never writes parse logs, and sync (the
  only parse-log writer) is blocked while the flag is set.

### 1.3 Gate the real-data entry points

A shared confirm dialog on each blocked action:

> **Demo data present**
> Delete demo data before adding real data, so demo and real transactions don't mix.
> **[Delete demo data]** **[Cancel]**

Behavior: **Delete demo data** runs `demoDataSeeder.deleteDemoData()` and dismisses the
dialog. It does **not** auto-run the blocked action — the user re-taps Sync/Import/Save
(the user explicitly chose "re-tap manually"). **Cancel** dismisses, blocking nothing
further.

Gated entry points:

| Entry point | Where it happens | Dialog trigger |
|---|---|---|
| SMS sync | `ParserViewModel.sync()` and `TransactionsViewModel.sync()` | When `demoDataLoaded` is true at sync start |
| Manual entry | `ManualEntryViewModel.save()` | When `demoDataLoaded` is true at save |
| CSV import | `SettingsViewModel.importCsv()` | When `demoDataLoaded` is true at import |

Each ViewModel observes `demoDataPreferences.demoDataLoaded` (collected into its
`UiState`) and, when true, opens the dialog instead of running the action. A VM-level
`demoDeleteRequested`/dialog flag drives the dialog in the composable; confirm calls a
VM function that invokes `deleteDemoData()`.

### 1.4 Backstop guard in `SmsSyncUseCase.sync()`

Defense in depth: if `demoDataLoaded` is true at sync start, return
`SyncResult(error = "Delete demo data before syncing real SMS.")` immediately instead of
scanning. `SmsSyncUseCase` gains a `DemoDataPreferences` constructor dependency. This
guards callers that don't show the dialog (e.g. the Unparsed SMS "Re-sync now" path).

### 1.5 Settings row

New Settings row **"Delete demo data"** (above or below "Load demo data"), only shown
when `demoDataLoaded` is true. Tapping opens the same confirm dialog (destructive-action
styling). This lets the user clean up proactively, before trying to add real data.

### 1.6 Testing (Feature 1)

- `DemoDataPreferences` round-trip (set true → flow emits true; set false → false).
- `seedIfEmpty()` sets the flag after insert; leaves it unchanged when it returns 0
  (already seeded).
- `deleteDemoData()` calls `deleteAll()` and clears the flag.
- Each gated VM: action with flag true → dialog state set, repository/use case **not**
  called; confirm → `deleteDemoData()` invoked; cancel → nothing.
- After confirming delete, the blocked action is NOT auto-run (verified by
  `verify { ... }` never-called).
- `SmsSyncUseCase.sync()` with flag true → `SyncResult(error = ...)`, no scanning.
- Settings VM: row visibility from `demoDataLoaded`; confirm calls delete.
- `TransactionDao.deleteAll()` reflected in repository impl + test.

## Feature 2 — Bulk-categorize flow

### 2.1 New bottom-nav tab "Categorize"

`BottomNavItem.Categorize` (route `"categorize"`, label "Categorize",
icon e.g. `Icons.Default.Sell`), added to `BottomNavItem.items` and the
`BottomNavItem` bar in `MainActivity` (it renders from `items`, so this is additive).
Single-purpose screen — no other utilities move into it.

### 2.2 `CategorizeViewModel` (new, `ui/screens/categorize/`)

- Combines `TransactionRepository.getAllTransactions()` and
  `CategoryRepository.getAllCategories()`.
- State: `queue: List<Transaction>`, `index: Int`, `categories: List<Category>`,
  `isDone: Boolean`, plus an `assigned` counter for a completion message.
- **Queue ordering:** uncategorized transactions first (`categoryId == null`), then the
  rest, each group sorted by `transactionDate` descending (newest first) — mirrors the
  Transactions screen list and surfaces the pain (SMS-parsed, uncategorized) first.
- `current: Transaction?` = `queue.getOrNull(index)`.
- `assignCategory(categoryId: Long?)`: calls
  `transactionRepository.updateTransactionCategory(current.id, categoryId)`, then
  advances `index` by one. `null` is allowed → "None".
- `skip()`: advances `index` without writing.
- When `index >= queue.size` → `isDone = true`.
- The queue is a **snapshot** at collection time; re-categorizing an already-assigned
  transaction changes its position in the queue ordering but the snapshot order stands
  (no re-sort mid-flow — keeps progress simple).

### 2.3 `CategorizeScreen` (new, `ui/screens/categorize/`)

- Scaffold + TopAppBar (back or implicit bottom-nav — it's a nav item, so no back
  button needed; bottom bar stays visible).
- Card shows: amount (`formatPaisa`), type, description, bank name (from banks flow),
  date, and a category dropdown (ExposedDropdownMenu, same pattern as
  `TransactionDetailSheet` — user chose dropdown over chip grid). "None" as the first
  option.
- Actions: **Skip** (TextButton) and **Save & Next** (the dropdown selection applies
  immediately via `assignCategory`, or a Save button applies then advances — design
  detail: dropdown selection applies on confirm via Save button).
- Progress: "3 of 12" (1-based index over queue size).
- Done state: "All done — N categorized." with a button to reset/restart.
- Empty state: "No transactions yet" when the queue is empty.

### 2.4 Reuse & no demo gate

- Uses the existing `TransactionRepository.updateTransactionCategory()` — the same path
  the Transactions detail sheet uses.
- No demo gating here: categorizing is read/write on existing rows, not a real-data
  entry point. Works whether or not demo data exists.

### 2.5 Testing (Feature 2)

- Queue ordering: uncategorized first, then dated desc within each group.
- `assignCategory(id)` → repository called with current id + category, index advances.
- `assignCategory(null)` → "None" written (categoryId null).
- `skip()` → index advances, repository not called.
- Progress: index/queueSize; done when exhausted.
- Done state and empty state.

## Out of scope

- Full "Delete all data" wipe (user chose demo-delete only).
- Auto-categorization / suggestion engine (user chose manual picker flow).
- Swipe gestures (user chose card-by-card with dropdown).
- Moving Unparsed SMS / demo management into the new tab (user chose single-purpose
  categorize tab; those stay in Settings).

## Global constraints (project)

- Package root `com.smsexpensetracker`; money as paisa `Long`; no code comments unless
  the plan's code blocks include them.
- Build gate (no lint/typecheck): `./gradlew assembleDebug` and
  `./gradlew cleanTestDebugUnitTest testDebugUnitTest`.
- JUnit 4 + MockK + `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`,
  `Dispatchers.setMain`). 332 tests green at baseline.
- Commit directly to `main`. Never stage the pre-existing dirty
  `DashboardViewModel.kt` or `opencode.json`, or untracked plan/spec docs.

## File structure map

| File | Change |
|---|---|
| `app/src/main/java/com/smsexpensetracker/core/settings/DemoDataPreferences.kt` | New |
| `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt` | Add `deleteAll()` |
| `app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt` | Add `deleteAll()` delegate |
| `app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt` | Add `deleteAll()` |
| `app/src/main/java/com/smsexpensetracker/data/demo/DemoDataSeeder.kt` | Set flag; add `deleteDemoData()` |
| `app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt` | Backstop guard |
| `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModel.kt` | Gate CSV import; delete-demo row state |
| `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt` | Delete-demo row + dialog |
| `app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserViewModel.kt` | Gate sync |
| `app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserScreen.kt` | Barrier dialog |
| `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt` | Gate sync |
| `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt` | Barrier dialog |
| `app/src/main/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryViewModel.kt` | Gate save |
| `app/src/main/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryScreen.kt` | Barrier dialog |
| `app/src/main/java/com/smsexpensetracker/ui/navigation/BottomNavItem.kt` | Add Categorize |
| `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt` | `categorize` route |
| `app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeViewModel.kt` | New |
| `app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeScreen.kt` | New |
| Tests | `DemoDataPreferencesTest`, `DemoDataSeederTest` (extend), `TransactionRepositoryImplTest` (extend), `SmsSyncUseCaseTest` (extend), gated VM tests, `CategorizeViewModelTest` |
