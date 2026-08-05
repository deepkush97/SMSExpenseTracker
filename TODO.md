# SMS Expense Tracker — Build Checklist

> **Progressive build plan.** Each task is independent and produces a testable increment.  
> Complete tasks in order — each builds on the prior.

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
- [x] Implement all Room entities: `BankEntity`, `SmsRuleEntity`, `TransactionEntity`, `CategoryEntity`, `TransactionLabelEntity`, `UserCategoryRuleEntity`, `ParseLogEntity`, `SyncMetaEntity`
- [x] Implement all DAOs with `@Insert`, `@Update`, `@Delete`, `@Query` methods
- [x] Implement `Converters` class (`@TypeConverter` for enums, `LocalDateTime`, `Long` (paisa))
- [x] Implement `SmsExpenseDatabase` (`@Database` with all entities, version 1, `exportSchema = true`)
- [x] Implement `SeedDatabaseCallback` (`RoomDatabase.Callback.onCreate()`) — inserts 6 banks, 14 categories, 14 SMS_RULE template seed rows
- [x] Configure Room schema export in `build.gradle.kts`
- [ ] Implement `MIGRATION_1_2` example and migration test skeleton
- [ ] **Verify:** Migration test passes; database helper queries return seed data

### [x] 3. Domain Models & Repository Interfaces
- [x] Implement domain models: `Transaction`, `Bank`, `SmsRule`, `Category`, `UserCategoryRule`, `ParseLog`, `SyncMeta`, `TransactionLabel`
- [x] Implement value objects: `SenderId`, `ParsedResult`, `ConfidenceScore`, `SyncProgress`, `SyncRange`
- [x] Implement repository interfaces: `TransactionRepository`, `BankRepository`, `SmsRuleRepository`, `CategoryRepository`, `ParseLogRepository`, `SyncMetaRepository`
- [x] Implement use case stubs: `ParseSmsUseCase`, `GetTransactionsUseCase`, `LabelTransactionUseCase`, `SyncSmsUseCase`, `ExportCsvUseCase`
- [ ] **Verify:** All interfaces compile; use cases are injectable via Hilt

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
- [ ] **Verify:** Data layer compiles; Room integration tests pass with in-memory DB

### [ ] 7. Sync Use Case (Debounce + Batching)
- [x] Implement `SmsSyncUseCase` — orchestrates `SmsReader.readSms(range)` -> debounce(300ms) -> chunk(100) -> `ParserEngine.parseBatch()` -> `TransactionRepository.insertBatch()` -> emit progress
- [x] Implement deduplication: SHA-256 `smsBodyHash` in `TransactionEntity`, `@Insert(onConflict = IGNORE)`
- [ ] Implement incremental sync: query only SMS with date > `lastSyncAt` _(deferred — full-scan + hash-dedup chosen; background worker is a separate sub-project)_
- [ ] Implement `SmsSyncWorker` (WorkManager) — periodic background sync with constraints (battery not low) _(deferred — full-scan + hash-dedup chosen; background worker is a separate sub-project)_
- [~] **Verify:** End-to-end sync test with mock SMS data produces correct transactions (needs device/emulator smoke test)

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
- [ ] Implement `ErrorBanner`, `ErrorSnackbar`, `ErrorDialog` composables
- [x] Implement `DashboardViewModel`, `TransactionsViewModel`, `ParserViewModel`, `SettingsViewModel` stubs
- [x] **Verify:** `./gradlew assembleDebug` compiles cleanly

### [x] 10. Dashboard Screen
- [x] Implement summary cards: Total Spent, Total Received, Net (with animated counter)
- [x] Implement per-bank grouped bar chart (Vico `ColumnCartesianLayer`)
- [x] Implement monthly credit vs debit line chart (Vico `LineCartesianLayer`)
- [x] Implement category breakdown donut chart (Vico `PieChartHost`)
- [x] Implement recent transactions list (last 5, tappable -> detail sheet)
- [x] Implement loading state (shimmer/skeleton cards)
- [ ] Implement Dashboard empty state (`EmptyState` with "Sync SMS" CTA) _(optional refinement)_
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

### [-] 12. Manual Transaction Entry Screen
- [x] Implement form composable: Amount (currency input), Type (radio: Credit/Debit), Date (date picker), Account, Payee, Category dropdown, Reference
- [x] Implement form validation (amount > 0, date required, max lengths)
- [x] Implement inline error display per field
- [x] Implement save flow: validate -> insert `TransactionEntity(parseMethod=MANUAL)` -> close -> list updates
- [~] **Verify:** Manual entry saves to database; appears in transaction list (needs device/emulator smoke test)

### [x] 13. Parser Test Screen
- [x] Implement SMS text input area (multiline `OutlinedTextField`)
- [x] Implement bank selector dropdown (auto-detect or manual select)
- [x] Implement "Test Parse" button
- [x] Implement parsed result display: all extracted fields with labels + confidence badge
- [ ] Implement regex rule editor (edit `contentRegex`, map named groups to fields) _(deferred to Settings SMS rule mgmt, Task 14)_
- [x] Implement "Add as Transaction" button (promote parsed result to manual entry)
- [ ] Implement log viewer section (recent parse failures from FileLogger) _(blocked on Task 8)_
- [x] Connect to `ParserViewModel`
- [x] **Verify:** Pasting an SMS and tapping "Test Parse" shows correctly extracted fields

### [ ] 14. Settings Screen
- [x] Implement bank management: list banks, add new, edit sender pattern
- [x] Implement SMS rule management per bank: list, add, edit, delete, enable/disable rules
  - [x] **Add new bank rules** — user can add a regex rule (bank, `contentRegex` pattern, description) for a new SMS format/bank, not just edit seeded rules; new rules must be picked up by `ParserEngine` (Parser Test screen) without a rebuild
  - [x] Rule editor tests pattern against a pasted sample SMS before saving
  - [x] Rule patterns support {amount}/{description} template syntax (legacy regex still works)
  - [x] Seed now holds 14 template rules across 6 banks (HDFC 7, ICICI 3, Pluxee 3, DCB 1) covering every `scripts/push_test_sms.sh` pattern — no parse failures out of the box
- [x] Implement category management: list, add, edit, delete
  - [x] **Searchable icon picker** — category Add/Edit dialog has a "Search icons" field over a scrollable grid of ~120 curated Material icons (name + keyword search)
- [ ] Implement sync controls: trigger re-sync, select range, view last sync time
- [x] Implement CSV export/import buttons
- [x] Demo data is opt-in — the app starts empty (no auto-seed on launch); Settings → Data → **Load demo data** seeds 60 transactions (second tap reports "Demo data already loaded")
  - [x] **Demo-data gate** — while demo data is present, real entry is blocked (Transactions sync, Manual Entry save, Parser add-as-transaction, CSV import) with a "Demo data present" dialog; Settings → **Delete demo data** wipes the demo rows and unblocks everything
- [x] Implement log viewer: view/share/clear error logs
  - [x] **Unparsed SMS review screen** — Settings → "Unparsed SMS" lists failed SMS bodies (deduped, with count/bank/sender), "Fix" opens the Rule Editor pre-filled with the SMS body + detected bank, and "Re-sync now" clears stale FAILED parse logs before re-running sync
- [ ] Implement about section: app version, build info
- [ ] **Verify:** Settings operations (add bank, edit rule, export CSV) persist correctly

---

## Phase 4: Onboarding & Sync

### [ ] 15. Onboarding Flow
- [ ] Implement first-launch detection (`SharedPreferences` flag)
- [ ] Implement permission explanation screen with illustration
- [ ] Implement `READ_SMS` permission request with rationale dialog
- [ ] Handle permission denied: show "Open Settings" banner
- [ ] Implement sync range picker bottom sheet (1d/1w/2w/1m/3m/All)
- [ ] Implement "Sync SMS" button with loading state
- [ ] Integrate `SmsSyncWorker` trigger after range selection
- [ ] **Verify:** On first launch, empty dashboard shows, sync flow completes end-to-end

### [ ] 16. Sync Progress & Dashboard Updates
- [ ] Observe `WorkManager.getWorkInfoByIdFlow()` in DashboardViewModel
- [ ] Implement sync progress banner: "Scanning SMS... {processed}/{total} ({percent}%)"
- [ ] Implement incremental dashboard updates as batches complete
- [ ] Implement sync complete banner: "X SMS scanned, Y transactions found, Z unparsed"
- [ ] Handle sync failure: error banner with retry
- [~] Implement unparsed SMS list (tappable from completion banner) _(shipped as Settings → Unparsed SMS instead — the completion-banner entry point is deferred)_
- [ ] Implement re-sync option in Settings
- [ ] **Verify:** Progress banner updates live; dashboard populates incrementally

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

### [ ] 18. Testing & Release Preparation
- [ ] Write Room integration tests: DAO CRUD, batch insert, deduplication
- [ ] Write repository tests with mock DAOs
- [ ] Write use case tests with mock repositories
- [ ] Write ViewModel tests with `CoroutineRule` + `StandardTestDispatcher`
- [ ] Generate ADB test SMS script for emulator testing
- [ ] Build release APK/AAB, smoke test on physical device
- [ ] Verify ProGuard rules don't break functionality
- [ ] **Verify:** `./gradlew lint` passes; `./gradlew testDebugUnitTest` 100% pass; release APK works

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
