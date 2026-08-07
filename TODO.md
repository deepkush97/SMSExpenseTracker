# SMS Expense Tracker — Build Checklist

> **Progressive build plan.** Each task is independent and produces a testable increment.  
> Complete tasks in order — each builds on the prior.

---

## Finalization Track — Priority Order (for actual use)

> What remains to ship a usable app. Unit suite: **429 tests green** (`./gradlew testDebugUnitTest`).
> P0 = blocks daily use · P1 = strongly recommended · P2 = polish/later.

### P0 — Must land first
- [ ] **F1. Real-device end-to-end smoke test** — install on a physical device → grant `READ_SMS` → Sync SMS → verify transactions on Dashboard/List → re-sync (no duplicates, `smsBodyHash`) → CSV export round-trip → Unparsed SMS review → run migration androidTests on device. _(folds in Tasks 7/12/16 verifies + `scripts/push_test_sms.sh`)_
- [x] **F2. Live sync progress** — live "Scanning SMS... {processed}/{total} ({percent}%)" banner on the Transactions screen, driven by `SmsSyncUseCase.progress` (`processed/total/percent`). (Task 16 done 2026-08-07)

### P1 — Strongly recommended
- [x] **F3. Sync controls in Settings** — last-sync time, Re-sync button, sync-range picker (1d/1w/2w/1m/3m/All); `SyncRange` re-landed and wired → `SmsReader.readSms(dateRange)` in `SmsSyncUseCase.sync()`. (Tasks 14/15 done 2026-08-07)
- [x] **F4. Auto-categorization on sync** — `AutoCategoryEngine` keyword/merchant rule engine; `UserCategoryRule` + `TransactionLabel` entities, DAOs, and repositories re-landed and applied during sync. (SOLUTION_DESIGN §10.8)
- [x] **F5. Dashboard empty state** — "Get started" card on empty Dashboard (Try demo data / Sync SMS / dismiss) doubles as onboarding safety net. (Task 10; implemented with the new-user onboarding flow, see `docs/superpowers/specs/2026-08-05-new-user-onboarding-design.md`)
- [ ] **F6. Release build** — signed release APK (keystore), verify ProGuard, device smoke test; About section shows version from `BuildConfig` instead of hardcoded "Version 1.0". (Tasks 14/18)
- [x] **F11. On-arrival SMS capture** — `SMS_RECEIVED` broadcast receiver parses + records new bank SMS instantly (no manual sync). Request `RECEIVE_SMS` alongside `READ_SMS`. Spec: `docs/superpowers/specs/2026-08-05-on-arrival-sms-capture-design.md`. On-arrival = fast path; full-scan "Sync SMS" remains the re-attempt safety net for failed parses. _(Implemented 2026-08-05: `SmsIncomingReceiver` + `handleIncomingSms` rule-match fallback; 429 unit tests green. Device check folded into F1.)_

### P2 — Polish / later
- [x] **F7. Error components** — shared `ErrorBanner` / `ErrorSnackbar` / `ErrorDialog` (`ErrorBanner` wired on Transactions). (Task 9 done 2026-08-07)
- [x] **F8. Room DAO integration tests** — in-memory DB CRUD, batch insert, dedup (androidTest). (Task 18 done 2026-08-07)
- [ ] **F9. CI/CD** — GitHub Actions: lint+test, debug APK, signed release. (Task 17)
- [ ] **F10. Incremental sync + `SmsSyncWorker`** — background periodic sync (deferred sub-project). (Task 7)

---

## Phase 0: Project Foundation

### [x] 1. Project Scaffold & Build Setup
- [x] Initialize Android project (Kotlin, Compose, Min SDK 28, Target SDK 35)
- [x] Create module structure: `core/`, `data/`, `domain/`, `presentation/`, `di/`
- [x] Configure `settings.gradle.kts` and `build.gradle.kts` (project + app)
- [x] Add all dependencies: Room, Hilt, Compose BOM, Navigation, Vico, Timber, Coroutines, MockK
- [x] Create `libs.versions.toml` (Gradle Version Catalog)
- [x] Configure Hilt `@HiltAndroidApp` Application class
- [x] Add `proguard-rules.pro` with keep rules for Room, Hilt, Compose, Coroutines
- [x] Add `AndroidManifest.xml` with permissions (`READ_SMS`, `RECEIVE_SMS`)
- [x] **Verify:** `./gradlew assembleDebug` compiles cleanly

### [x] 2. Database Foundation
- [x] Implement all Room entities: `BankEntity`, `SmsRuleEntity`, `TransactionEntity`, `CategoryEntity`, `ParseLogEntity`, `SyncMetaEntity` _(`TransactionLabelEntity`, `UserCategoryRuleEntity` added in Task 2, pruned 2026-08-06, re-landed with **F4** on 2026-08-07)_
- [x] Implement all DAOs with `@Insert`, `@Update`, `@Delete`, `@Query` methods
- [x] Implement `Converters` class (`@TypeConverter` for enums, `LocalDateTime`, `Long` (paisa))
- [x] Implement `SmsExpenseDatabase` (`@Database` with all entities, version 7, `exportSchema = true`; started at version 1; bumped 5→6 on 2026-08-06 when the two unused label tables were pruned, then 6→7 on 2026-08-07 when they were re-landed)
- [x] Implement `SeedDatabaseCallback` (`RoomDatabase.Callback.onCreate()`) — inserts 6 banks, 14 categories, 14 SMS_RULE template seed rows
- [x] Configure Room schema export in `build.gradle.kts`
- [x] Implement migrations & migration tests — DB is at **version 7**; `MIGRATION_1_2`, `2_3`, `3_4`, `4_5`, `5_6`, `6_7` with 6 androidTest cases (`MigrationTest.kt`; `5_6` drops the pruned `transaction_labels`/`user_category_rules` tables, `6_7` recreates them)
- [~] **Verify:** Migration tests pass on a device/emulator (folded into **F1**)

### [x] 3. Domain Models & Repository Interfaces
- [x] Implement domain models: `Transaction`, `Bank`, `SmsRule`, `Category`, `ParseLog`, `SyncMeta` _(`UserCategoryRule`, `TransactionLabel` pruned 2026-08-06, re-landed with **F4** on 2026-08-07)_
- [x] Implement value objects: `SenderId`, `ParsedResult`, `ConfidenceScore`, `SyncProgress`, `SyncRange` _(`SyncRange` pruned 2026-08-06, re-landed with **F3** on 2026-08-07)_
- [x] Implement repository interfaces: `TransactionRepository`, `BankRepository`, `SmsRuleRepository`, `CategoryRepository`, `ParseLogRepository`, `SyncMetaRepository`
- [x] Implement use case stubs: `GetTransactionsUseCase`, `SyncSmsUseCase`, `ExportCsvUseCase` _(`ParseSmsUseCase`, `LabelTransactionUseCase` pruned 2026-08-06; not re-landed)_
- [x] **Verify:** All interfaces compile; use cases are injectable via Hilt (`@Inject` wired in DI modules + ViewModels)

---

## Phase 1: SMS Parsing Core

### [x] 4. SMS Parser Engine
- [x] Implement `SenderDetector` — parse TRAI DLT sender IDs (`AD-HDFCBK-S` -> `HDFCBK`), strip suffix, match against bank patterns (contains/starts-with)
- [x] Implement `RegexParser` — apply regex rules by priority, extract capture groups (`amount`, `description`)
- [x] Implement `TypeInferrer` — keyword-based type inference (debited->DEBIT, spent->DEBIT, credited->CREDIT, refunded->CREDIT)
- [x] Implement `ConfidenceScorer` — score based on matched groups + keyword presence (0.0–1.0)
- [x] Implement `ParserEngine` — orchestrates sender detection, rule loading, regex matching, type inference, confidence scoring
- [x] Implement `ParseLog` recording for every parse attempt
- [ ] **Verify:** Parser correctly extracts all fields from the 14 real SMS patterns
  - [x] Covered by `ParserEngineTest`, `RegexParserTest`, `SenderDetectorTest`, `TypeInferrerTest`, `ConfidenceScorerTest` (22 parser tests, part of the 429 green)

### [x] 5. Parser Unit Tests (Real SMS Patterns)
- [x] Create test data class with all 14 SMS strings across 4 banks
- [x] Write all pattern tests with verified amounts and descriptions
- [x] Write confidence score tests
- [x] Write unknown sender test
- [x] **Verify:** 22 tests pass; `./gradlew testDebugUnitTest` is green

---

## Phase 2: Data Layer

### [-] 6. Repository & Data Source Implementation
- [x] Implement `SmsReader` — query `content://sms`, filter by sender ID pattern, return `Flow<List<SmsMessage>>`, support date range filtering
- [x] Implement `PermissionManager` — check/grant `READ_SMS` permission, request runtime, open settings fallback
- [x] Implement `TransactionRepositoryImpl` — DAO delegation, batch insert with dedup (`smsBodyHash`), Flow-based query, category label assignment
- [x] Implement `BankRepositoryImpl` — CRUD for banks and SMS rules
- [x] Implement `SmsRuleRepositoryImpl` — load active rules by bank, rule priority ordering
- [x] Implement `CategoryRepositoryImpl` — CRUD for categories and auto-category rules
- [x] Implement `SyncMetaRepositoryImpl` — track last sync time, range, status, progress
- [x] Implement `ParseLogRepositoryImpl` — record and query parse logs
- [x] **Verify:** Data layer compiles; repository unit tests pass (6 repo test classes, part of the 429 green). Room in-memory integration tests → **F8**

### [x] 7. Sync Use Case (Debounce + Batching)
- [x] Implement `SmsSyncUseCase` — orchestrates `SmsReader.readSms()` -> chunk(100) -> `ParserEngine.parse()` -> `TransactionRepository.insertBatch()` -> emit progress; wired to UI (Transactions sync button + Unparsed re-sync)
- [x] Implement deduplication: SHA-256 `smsBodyHash` in `TransactionEntity`, `@Insert(onConflict = IGNORE)`
- [ ] Implement incremental sync: query only SMS with date > `lastSyncAt` _(deferred — full-scan + hash-dedup chosen; see **F10**)_
- [ ] Implement `SmsSyncWorker` (WorkManager) — periodic background sync with constraints (battery not low) _(deferred — see **F10**)_
- [~] **Verify:** End-to-end sync on device/emulator with `scripts/push_test_sms.sh` (folded into **F1**)

### [x] 8. Infrastructure: Logging & Backup
- [x] Implement `FileLogger` — write to `filesDir/logs/{error_log, parse_failures, unparsed_sms, crash_log}.txt`
- [x] Implement `FileLoggingTree` (Timber.Tree) — forwards log calls to FileLogger
- [x] Implement `CsvExporter` / `CsvImporter` — CSV export (query all transactions, format as CSV, share via FileProvider), CSV import (read CSV, validate, deduplicate, bulk insert)
- [x] Implement log viewer UI in Settings (File Logs + Parse Log sections)
- [x] **Verify:** CSV export writes valid file; CSV import round-trips correctly

---

## Phase 3: Core UI

### [x] 9. Navigation, Theme & Infrastructure
- [x] Implement Material 3 theme (finance colors: blue primary, green secondary, red error)
- [x] Implement NavHost with bottom navigation: Dashboard, Transactions, Parser Test, Settings
- [x] Implement `MainActivity` with `@AndroidEntryPoint`, Scaffold with bottom bar
- [x] Implement `EmptyState` composable (icon, title, subtitle, action button)
- [x] Implement `ErrorBanner`, `ErrorSnackbar`, `ErrorDialog` composables → **F7**
- [x] Implement `DashboardViewModel`, `TransactionsViewModel`, `ParserViewModel`, `SettingsViewModel` (+ `Bank`, `Category`, `RuleEditor`, `LogViewer`, `UnparsedSms`, `Categorize`, `ManualEntry` VMs)
- [x] **Verify:** `./gradlew assembleDebug` compiles cleanly

### [x] 10. Dashboard Screen
- [x] Implement summary cards: Total Spent, Total Received, Net (with animated counter)
- [x] Implement per-bank grouped bar chart (Vico `ColumnCartesianLayer`)
- [x] Implement monthly credit vs debit line chart (Vico `LineCartesianLayer`)
- [x] Implement category breakdown donut chart (Vico `PieChartHost`)
- [x] Implement recent transactions list (last 5, tappable -> detail sheet)
- [x] Implement loading state (shimmer/skeleton cards)
- [x] Implement Dashboard empty state (`GetStartedCard` on empty Dashboard) → **F5**
- [x] Connect `DashboardViewModel` + `GetTransactionsUseCase` for live data
- [x] **Verify:** Dashboard renders charts with seed/test data

### [x] 11. Transaction List Screen
- [x] Implement search bar with debounced query
- [x] Implement filter chips: All, Credit, Debit, Bank selector
- [x] Implement `LazyColumn` transaction list with date headers and group separators
- [x] Implement transaction row composable (amount, type icon, payee, date, category chip)
- [x] Implement Transaction Detail bottom sheet (all fields, category picker, edit label)
  - [x] **Edit transaction details** — tap a transaction row → the bottom sheet edits Amount, Type, Date, Bank, Description, and Category with Cancel / Update CTAs; the update persists and the list refreshes live (DB Flow), preserving `smsBodyHash` so re-sync never duplicates the edited row
- [x] Implement category picker with searchable dropdown
- [x] Implement FAB (+) -> navigates to Manual Entry screen
- [x] Implement Transaction List empty state
- [x] Connect to `TransactionsViewModel` + `GetTransactionsUseCase` with live filter
- [x] **Bulk categorize flow** — Categorize tab (between Transactions and Parser): card-by-card category assignment (None + categories dropdown, Skip, Start over)
- [x] **Verify:** Transactions render, search filters, category assignment works

### [x] 12. Manual Transaction Entry Screen
- [x] Implement form composable: Amount (currency input), Type (radio: Credit/Debit), Date (date picker), Account, Payee, Category dropdown, Reference
- [x] Implement form validation (amount > 0, date required, max lengths)
- [x] Implement inline error display per field
- [x] Implement save flow: validate -> insert `TransactionEntity(parseMethod=MANUAL)` -> close -> list updates
- [~] **Verify:** Manual entry saves to database; appears in transaction list (folded into **F1** device smoke test)

### [x] 13. Parser Test Screen
- [x] Implement SMS text input area (multiline `OutlinedTextField`)
- [x] Implement bank selector dropdown (auto-detect or manual select)
- [x] Implement "Test Parse" button
- [x] Implement parsed result display: all extracted fields with labels + confidence badge
- [x] Implement regex rule editor — shipped in Settings → Banks & Rules → Rule Editor (Task 14)
- [x] Implement "Add as Transaction" button (promote parsed result to manual entry)
- [x] Implement log viewer — shipped via Settings → Logs (recent parse failures + parse log)
- [x] Connect to `ParserViewModel`
- [x] **Verify:** Pasting an SMS and tapping "Test Parse" shows correctly extracted fields

### [-] 14. Settings Screen
- [x] Implement bank management: list banks, add new, edit sender pattern
- [x] Implement SMS rule management per bank: list, add, edit, delete, enable/disable rules
  - [x] **Add new bank rules** — user can add a regex rule (bank, `contentRegex` pattern, description) for a new SMS format/bank, not just edit seeded rules; new rules must be picked up by `ParserEngine` (Parser Test screen) without a rebuild
  - [x] Rule editor tests pattern against a pasted sample SMS before saving
  - [x] Rule patterns support {amount}/{description} template syntax (legacy regex still works)
  - [x] Seed now holds 14 template rules across 6 banks (HDFC 7, ICICI 3, Pluxee 3, DCB 1) covering every `scripts/push_test_sms.sh` pattern — no parse failures out of the box
- [x] Implement category management: list, add, edit, delete
  - [x] **Searchable icon picker** — category Add/Edit dialog has a "Search icons" field over a scrollable grid of ~120 curated Material icons (name + keyword search)
- [x] Implement sync controls: trigger re-sync, select range, view last sync time → **F3** (re-sync already exists via Unparsed SMS screen)
- [x] Implement CSV export/import buttons
- [x] Demo data is opt-in — the app starts empty (no auto-seed on launch); Settings → Data → **Load demo data** seeds 60 transactions (second tap reports "Demo data already loaded")
  - [x] **Demo-data gate** — while demo data is present, real entry is blocked (Transactions sync, Manual Entry save, Parser add-as-transaction, CSV import) with a "Demo data present" dialog; Settings → **Delete demo data** wipes the demo rows and unblocks everything
- [x] Implement log viewer: view/share/clear error logs
  - [x] **Unparsed SMS review screen** — Settings → "Unparsed SMS" lists failed SMS bodies (deduped, with count/bank/sender), "Fix" opens the Rule Editor pre-filled with the SMS body + detected bank, and "Re-sync now" clears stale FAILED parse logs before re-running sync
- [~] Implement about section: app version, build info — section exists but version is hardcoded "Version 1.0" → use `BuildConfig` in **F6**
- [~] **Verify:** Settings operations (add bank, edit rule, export CSV) persist correctly — covered by unit tests; device check in **F1**

---

## Phase 4: Onboarding & Sync

### [-] 15. Onboarding Flow
- [x] Implement `READ_SMS` permission request with rationale dialog — shipped in Transactions screen empty state (sync CTA requests permission, shows "Open Settings" fallback)
- [x] Implement "Sync SMS" button with loading state — Transactions empty state + header button; Unparsed SMS has "Re-sync now"
- [x] Implement first-launch detection (DataStore `onboarding_complete` flag) → **F5** (3-page welcome flow at first launch; Dashboard card is the safety net)
- [ ] Implement dedicated permission explanation screen with illustration → optional (current inline flow works)
- [x] Implement sync range picker bottom sheet (1d/1w/2w/1m/3m/All) → **F3**
- [ ] Integrate `SmsSyncWorker` trigger after range selection → **F10** (deferred)
- [~] **Verify:** On first launch, empty dashboard shows, sync flow completes end-to-end → **F1**

### [-] 16. Sync Progress & Dashboard Updates
- [x] Observe sync progress (`SmsSyncUseCase.progress`, not WorkManager — no worker yet) in Transactions/Dashboard → **F2**
- [x] Implement sync progress banner: "Scanning SMS... {processed}/{total} ({percent}%)" → **F2**
- [x] Implement incremental dashboard updates as batches complete — DB is Flow-backed, dashboard/list refresh live
- [x] Implement sync complete banner: "X SMS scanned, Y transactions found, Z unparsed" — Transactions screen snackbar shows scanned/added/unparsed
- [x] Handle sync failure: error banner with retry — snackbar "Sync failed. Try again." + retry button
- [x] Implement unparsed SMS list — shipped as Settings → Unparsed SMS (completion-banner entry point deferred)
- [x] Implement re-sync option in Settings → **F3** (exists in Unparsed SMS screen)
- [~] **Verify:** Progress banner updates live; dashboard populates incrementally (banner implemented; device check → **F1**)

---

## Phase 5: Polish & Release

### [ ] 17. CI/CD Pipeline
- [ ] Create `.github/workflows/build.yml` with 3 jobs: lint+test, debug APK, release build
- [ ] Add Gradle caching and JDK setup
- [ ] Configure keystore decoding from `KEYSTORE_BASE64` secret
- [ ] Configure release signing in `app/build.gradle.kts`
- [ ] Add artifact upload for debug APK, release APK, release AAB
- [ ] Add GitHub Release attachment job (on tag)
- [ ] Test pipeline on a sample push
- [ ] **Verify:** `git push` triggers pipeline; release build produces signed APK

### [-] 18. Testing & Release Preparation
- [x] Write Room integration tests: DAO CRUD, batch insert, deduplication → **F8**
- [x] Write repository tests with mock DAOs — 6 repo test classes green
- [x] Write use case tests with mock repositories — `SmsSyncUseCaseTest`, `ExportCsvUseCaseTest`, `ImportCsvUseCaseTest`
- [x] Write ViewModel tests — `TransactionsViewModelTest`, `SettingsViewModelTest`, `CategorizeViewModelTest`, `UnparsedSmsViewModelTest`, `ThemeViewModelTest` (StandardTestDispatcher)
- [x] Generate ADB test SMS script — `scripts/push_test_sms.sh` exists
- [ ] Build release APK/AAB, smoke test on physical device → **F6** + **F1**
- [ ] Verify ProGuard rules don't break functionality → **F6**
- [~] **Verify:** `./gradlew testDebugUnitTest` 100% pass (429 green); `lint` not configured (AGENTS.md: build+test only); release APK works → **F1/F6**

---

## UI Polish (Sprint: 2026-07-31)

- [x] Indian currency formatting (₹12,34,567.89) everywhere
- [x] Font polish (typography scale)
- [x] Category chip/avatar contrast fixes
- [x] Transaction list rows — icon-forward, tonal cards, snappy press
- [x] Donut chart legend styling
- [x] Chart axis theming (no black-on-dark labels)
- [x] Pill search bar with clear button
- [x] Credit green / debit onSurface color in TransactionRow
- [x] Deleted `TransactionListItem.kt`; `DateSectionHeader` moved to `TransactionsScreen.kt`

### Smoke-Test Checklist (manual)

> Fold into **F1** real-device pass.

- [ ] Dark / Light / AMOLED modes: summary cards, recent transactions, chart axes (no black-on-dark labels), donut legend text
- [ ] Transactions list: avatars readable for bright and dark categories, credit green vs debit default color, pill search with clear button
- [ ] Amounts: `₹12,34,567.89` Indian grouping everywhere (dashboard, list, detail sheet, monthly banner)

---

## Legend

| Status | Meaning |
|--------|---------|
| `[ ]` | Not started |
| `[-]` | In progress |
| `[x]` | Complete |
| `[~]` | Blocked |

## Quick Reference

| Phase | Tasks | Dependencies |
|-------|-------|-------------|
| 0. Foundation | 1–3 | None (start here) |
| 1. Parsing Core | 4–5 | 1–3 |
| 2. Data Layer | 6–8 | 1–3 |
| 3. Core UI | 9–14 | 1–3, 6 |
| 4. Onboarding | 15–16 | 1–14 |
| 5. Polish | 17–18 | 1–16 |
| **Finalization** | **F1–F10** | all of the above |
