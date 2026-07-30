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
- [x] Implement `SeedDatabaseCallback` (`RoomDatabase.Callback.onCreate()`) — inserts 5 banks, 14 categories, 6 SMS_RULE seed rows
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
- [ ] Implement `ParseLog` recording for every parse attempt
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
- [ ] Implement `PermissionManager` — check/grant `READ_SMS` permission, request runtime, open settings fallback
- [x] Implement `TransactionRepositoryImpl` — DAO delegation, batch insert with dedup (`smsBodyHash`), Flow-based query, category label assignment
- [x] Implement `BankRepositoryImpl` — CRUD for banks and SMS rules
- [x] Implement `SmsRuleRepositoryImpl` — load active rules by bank, rule priority ordering
- [x] Implement `CategoryRepositoryImpl` — CRUD for categories and auto-category rules
- [x] Implement `SyncMetaRepositoryImpl` — track last sync time, range, status, progress
- [x] Implement `ParseLogRepositoryImpl` — record and query parse logs
- [ ] **Verify:** Data layer compiles; Room integration tests pass with in-memory DB

### [ ] 7. Sync Use Case (Debounce + Batching)
- [ ] Implement `SmsSyncUseCase` — orchestrates `SmsReader.readSms(range)` -> debounce(300ms) -> chunk(100) -> `ParserEngine.parseBatch()` -> `TransactionRepository.insertBatch()` -> emit progress
- [ ] Implement deduplication: SHA-256 `smsBodyHash` in `TransactionEntity`, `@Insert(onConflict = IGNORE)`
- [ ] Implement incremental sync: query only SMS with date > `lastSyncAt`
- [ ] Implement `SmsSyncWorker` (WorkManager) — periodic background sync with constraints (battery not low)
- [ ] **Verify:** End-to-end sync test with mock SMS data produces correct transactions

### [ ] 8. Infrastructure: Logging & Backup
- [ ] Implement `FileLogger` — write to `filesDir/logs/{error_log, parse_failures, unparsed_sms, crash_log}.txt`
- [ ] Implement `FileLoggingTree` (Timber.Tree) — forwards log calls to FileLogger
- [ ] Implement `BackupManager` — CSV export (query all transactions, format as CSV, write to Downloads/), CSV import (read CSV, validate, deduplicate, bulk insert)
- [ ] Implement log viewer UI in Settings or Parser Test screen
- [ ] **Verify:** CSV export writes valid file; CSV import round-trips correctly

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

### [ ] 11. Transaction List Screen
- [ ] Implement search bar with debounced query
- [ ] Implement filter chips: All, Credit, Debit, Bank selector
- [ ] Implement `LazyColumn` transaction list with date headers and group separators
- [ ] Implement transaction row composable (amount, type icon, payee, date, category chip)
- [ ] Implement Transaction Detail bottom sheet (all fields, category picker, edit label)
- [ ] Implement category picker with searchable dropdown
- [ ] Implement FAB (+) -> navigates to Manual Entry screen
- [ ] Implement Transaction List empty state
- [ ] Connect to `TransactionsViewModel` + `GetTransactionsUseCase` with live filter
- [ ] **Verify:** Transactions render, search filters, category assignment works

### [ ] 12. Manual Transaction Entry Screen
- [ ] Implement form composable: Amount (currency input), Type (radio: Credit/Debit), Date (date picker), Account, Payee, Category dropdown, Reference
- [ ] Implement form validation (amount > 0, date required, max lengths)
- [ ] Implement inline error display per field
- [ ] Implement save flow: validate -> insert `TransactionEntity(parseMethod=MANUAL)` -> close -> list updates
- [ ] **Verify:** Manual entry saves to database; appears in transaction list

### [ ] 13. Parser Test Screen
- [ ] Implement SMS text input area (multiline `OutlinedTextField`)
- [ ] Implement bank selector dropdown (auto-detect or manual select)
- [ ] Implement "Test Parse" button
- [ ] Implement parsed result display: all extracted fields with labels + confidence badge
- [ ] Implement regex rule editor (edit `contentRegex`, map named groups to fields)
- [ ] Implement "Add as Transaction" button (promote parsed result to manual entry)
- [ ] Implement log viewer section (recent parse failures from FileLogger)
- [ ] Connect to `ParserViewModel`
- [ ] **Verify:** Pasting an SMS and tapping "Test Parse" shows correctly extracted fields

### [ ] 14. Settings Screen
- [ ] Implement bank management: list banks, add new, edit sender pattern
- [ ] Implement SMS rule management per bank: list, add, edit, delete, enable/disable rules
- [ ] Implement category management: list, add, edit, delete
- [ ] Implement sync controls: trigger re-sync, select range, view last sync time
- [ ] Implement CSV export/import buttons
- [ ] Implement log viewer: view/share/clear error logs
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
- [ ] Implement unparsed SMS list (tappable from completion banner)
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
