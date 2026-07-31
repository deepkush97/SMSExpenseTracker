# Demo Data & Theme Mode — Design Spec

## Overview

Two related pieces of work to complete the UI before real SMS processing is built:

1. **Demo data** — seed realistic transactions on launch so the Dashboard, Transactions list, and charts render full content during UI development. This is **temporary scaffolding**: it is deleted when real SMS sync (Task 7) lands.
2. **Theme mode** — a user-facing, persisted choice of `System / Light / Dark / AMOLED` (AMOLED = dark with pure-black background), applied via the existing theme function. The picker lives in the Settings screen, which is currently a "coming soon" stub and gets its first real section.

No DB migration, no schema change, no `isDemo` column.

## Demo Data (temporary scaffolding)

### Trigger: seed-if-empty
- On every cold start, a `DemoDataSeeder` checks `transactionDao.count()`.
- If the transactions table is **empty**, it inserts a generated batch of demo transactions.
- If it already has data (any at all), it does nothing — no flag, no re-seeding, no state to manage.
- Because the check is a cheap `SELECT COUNT(*)`, running it on every launch is acceptable.

### DemoTransactionGenerator (`data/demo/`, pure Kotlin)
- Generates roughly 60–90 `TransactionEntity` rows spanning the **last 3 months** (dates spread across months so the dashboard monthly line chart has shape).
- Uses the 5 seeded bank ids (1–5) and most of the 14 seeded category ids (1–14). Amounts are in **paisa** (`Long`, rupees × 100).
- Mix of `CREDIT` and `DEBIT` with realistic payees: Zomato, Swiggy, BigBasket, Indian Oil, Netflix, Uber, Reliance Jio, Amazon, 1mg, Blinkit, salary (monthly CREDIT, category Salary), rent (monthly DEBIT, category Rent), refunds (CREDIT), Pluxee meal-card debits (category Food & Dining).
- Rows use `parseMethod = MANUAL`, `rawSms = ""`, `smsTimestamp = 0`, `createdAt = LocalDateTime.now()`, `transactionDate` at a realistic time of day.
- The class exposes a list-building function (e.g. `generate(): List<TransactionEntity>`) that is deterministic enough to unit-test.

### DemoDataSeeder (`data/demo/`, `@Singleton`)
- Injected with `TransactionDao` (already provided by `DatabaseModule`).
- Exposes `suspend fun seedIfEmpty()`: reads `count()`, inserts the generated batch only when the table is empty.
- Invoked from `SmsExpenseApp.onCreate` (the `@HiltAndroidApp` Application). `SmsExpenseApp` owns a `CoroutineScope(SupervisorJob() + Dispatchers.Default)` used to launch the seed.

### Removal plan
When real SMS sync (Task 7) lands:
1. Delete `DemoTransactionGenerator.kt`, `DemoDataSeeder.kt`, and the call site in `SmsExpenseApp`.
2. Clear app data once (dev-side) so leftover demo rows do not mix with real SMS transactions.
The spec/plan doc for Task 7 will note this.

## Theme Mode

### ThemeMode enum (`ui/theme/ThemeMode.kt`)
```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
```
Stored in DataStore as its `name` string (consistent with the app's enum-as-name convention in `Converters`).

### ThemePreferences (`core/settings/ThemePreferences.kt`)
- DataStore `preferencesDataStore` delegate named `"settings"` on `Context`.
- `val themeMode: Flow<ThemeMode>` — reads key `"theme_mode"`, defaults to `SYSTEM`.
- `suspend fun setThemeMode(mode: ThemeMode)` — writes the key.

### Dependency
- Add `androidx.datastore:datastore-preferences` to `libs.versions.toml` and `app/build.gradle.kts`.

### DI (`di/SettingsModule.kt`, new)
- `@Provides @Singleton fun provideThemePreferences(@ApplicationContext context: Context): ThemePreferences`.

### ThemeViewModel (`ui/theme/ThemeViewModel.kt`, `@HiltViewModel`)
- Injects `ThemePreferences`.
- Exposes `val themeMode: StateFlow<ThemeMode>` via `stateIn`.
- `fun onThemeModeChange(mode: ThemeMode)` — launches `setThemeMode`.
- Used by `MainActivity` (theme infrastructure, not a screen view model).

### MainActivity wiring
- `val themeViewModel: ThemeViewModel = hiltViewModel()`.
- Collect `themeMode` with `collectAsState` (the app's existing pattern).
- Compute:
  - `darkTheme = when (mode) { SYSTEM -> isSystemInDarkTheme(); LIGHT -> false; DARK, AMOLED -> true }`
  - `pureBlack = mode == AMOLED`
- Pass into the existing `SMSExpenseTrackerTheme(darkTheme = darkTheme, pureBlack = pureBlack) { ... }`. No changes to `Theme.kt` internals — it already accepts both params (the `pureBlack` flag is currently unused).

## Settings Screen Shell

Replaces the `SettingsScreen` stub ("Settings — coming soon").

### Layout
- A scrollable screen with an **Appearance** section: four selectable rows — System, Light, Dark, AMOLED ("pure black background" subtitle).
- Each row shows a leading icon and a selected indicator; tapping calls `onThemeModeChange`.
- A small **About** section at the bottom: app name and version text (static placeholder).
- No other settings cards or toggles yet — bank/category management, sync controls, and CSV export land here in later tasks.

### SettingsViewModel (replaces the stub)
- Injects `ThemePreferences`.
- Exposes `themeMode: StateFlow<ThemeMode>` + `onThemeModeChange(mode)`.

## Testing

- **DemoTransactionGeneratorTest** — pure JUnit: generated rows span bank ids 1–5 and category ids 1–14, amounts are positive paisa values, `transactionDate`s fall within the last 3 months, both CREDIT and DEBIT present, rows are distinct.
- **DemoDataSeederTest** — MockK: `seedIfEmpty()` inserts when `count() == 0`; does **not** insert when non-empty.
- **SettingsViewModelTest** — MockK + `runTest`: exposes the persisted mode; `onThemeModeChange` calls `setThemeMode`.
- **ThemePreferencesTest** — real in-memory DataStore over a temp file (`PreferenceDataStoreFactory`): default is `SYSTEM`; write/read round-trip.
- Existing 117 unit tests keep passing. No migration test changes.

## Out of Scope

- Parser Test screen (stays a stub).
- Real SMS parsing / sync / `SmsReader` integration (Task 7).
- `isDemo` column / DB migration / "Clear demo data" button.
