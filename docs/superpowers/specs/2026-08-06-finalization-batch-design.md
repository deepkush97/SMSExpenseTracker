# Finalization Batch — F2, F3, F4, F7, F8

**Date:** 2026-08-06
**Scope:** Five independent features shipped in one batch, one commit each:
F2 (live sync progress) → F3 (sync controls in Settings) → F4 (auto-categorization) → F7 (error components) → F8 (Room DAO integration tests).

**Deferred (explicit user choice):** F1 device smoke test, F6 release signing, F9 CI/CD, F10 incremental sync + worker.

---

## F2 — Live sync progress

### Current state
- `SmsSyncUseCase.progress: StateFlow<SyncProgress(processed, total, unparsed)>` exists and updates once per 100-SMS chunk.
- `TransactionsUiState` has `isSyncing: Boolean` and `syncMessage: String?`, but no progress. The header sync button shows a bare 22dp `CircularProgressIndicator` while syncing.

### Design
- Add `syncProgress: SyncProgress?` to `TransactionsUiState` (null when idle).
- `TransactionsViewModel` collects `smsSyncUseCase.progress` into a `MutableStateFlow`; in the `combine`, surface it as `syncProgress = if (isSyncing) progress else null` (progress never resets to empty after completion — it ends at `processed = total`, so nulling must key off the existing `_isSyncing` state).
- Header: when `syncProgress != null`, render a progress banner instead of the bare spinner:
  - `LinearProgressIndicator(progress = { percent })` where `percent = if (total > 0) processed.toFloat() / total else 0f`
  - Text: `Scanning SMS… {processed}/{total} ({percent}%)`
- Sync button keeps `isSyncing` for enabled/disabled.

### Tests
- `TransactionsViewModelTest`: progress emissions surface in `uiState.syncProgress`; percent math is correct; progress resets to null after sync completes.

---

## F3 — Sync controls in Settings

### Current state
- `SmsReader.readSms(senderFilter, dateRange: Pair<Long,Long>?)` already supports date range filtering but `SmsSyncUseCase.sync()` never passes one (full scan).
- `SyncRange` value object was deleted in the dead-code prune (recovered from git history).
- Settings has no sync section; last-sync time is not shown anywhere.

### Design
- **Re-land `SyncRange`** in `domain/value/SyncRange.kt`:
  ```kotlin
  data class SyncRange(
      val startTimestamp: Long,
      val endTimestamp: Long = System.currentTimeMillis()
  ) {
      companion object {
          private val now: Long get() = System.currentTimeMillis()
          val LAST_1D = SyncRange(now - 86_400_000L)
          val LAST_1W = SyncRange(now - 604_800_000L)
          val LAST_2W = SyncRange(now - 1_209_600_000L)
          val LAST_1M = SyncRange(now - 2_592_000_000L)
          val LAST_3M = SyncRange(now - 7_776_000_000L)
          val ALL = SyncRange(0L)
      }
  }
  ```
- **`SmsSyncUseCase.sync(range: SyncRange? = null)`**: pass `range?.let { it.startTimestamp to it.endTimestamp }` to `smsReader.readSms(dateRange = ...)`. Null keeps full-scan behavior; `SyncRange.ALL` maps to null internally (`dateRange = range?.takeUnless { it == SyncRange.ALL }?.let { it.startTimestamp to it.endTimestamp }`).
- **Settings UI**: new "Sync" section with:
  - Last sync time: from `syncMetaRepository.get()?.lastSyncTimestamp` formatted as relative/short date. Display updates after a sync.
  - Re-sync button → `SmsSyncUseCase.sync(selectedRange)` with busy indicator + result message (reuses `csvMessage`-style pattern → add `syncMessage`/`isSyncing` to `SettingsUiState`).
  - Range picker: `FilterChip`s for 1D / 1W / 2W / 1M / 3M / All, defaulting to All.
- `SettingsViewModel` gains `SmsSyncUseCase` + `SyncMetaRepository` deps.

### Tests
- `SmsSyncUseCaseTest`: `sync(range)` calls `smsReader.readSms(dateRange = expectedPair)`; `sync(null)` and `sync(SyncRange.ALL)` call `readSms()` with null dateRange.
- `SettingsViewModelTest`: range selection updates state; re-sync triggers use case with selected range; last-sync time loads and surfaces in state.

---

## F4 — Auto-categorization on sync

### Current state
- `UserCategoryRuleEntity` / `TransactionLabelEntity` and their domain models were deleted in the prune (recovered from git history).
- DB is at **version 6** (`6.json` committed, no label/rule tables).
- Categories are assigned only via manual edit / bulk-categorize UI. Sync inserts all transactions with `categoryId = null`.
- `CategoryRepository` has no rule methods.

### Design
- **Re-land entities** exactly as before (verified against git history):
  - `UserCategoryRuleEntity(id, pattern, categoryId)` — table `user_category_rules`, FK → `categories` CASCADE, index on `categoryId`.
  - `TransactionLabelEntity(id, transactionId, label)` — table `transaction_labels`, FK → `transactions` CASCADE, index on `transactionId`.
- **Domain models** `UserCategoryRule(id, pattern, categoryId)` and `TransactionLabel(id, transactionId, label)` with entity↔domain mappers.
- **DB v6 → v7**: `MIGRATION_6_7` recreates both tables (identical DDL to the historical schema). Commit `7.json`. Add `migrate6To7_createsLabelTables` androidTest.
- **DAOs**: `UserCategoryRuleDao` (getAll/insert/delete by category), `TransactionLabelDao` (insert, deleteForTransaction, getAllForTransaction).
- **`CategoryRepository`** gains: `getRules(): Flow<List<UserCategoryRule>>`, `insertRule(rule): Long`, `deleteRule(rule)`.
- **Label ids**: `TransactionRepository.insertBatch()` currently returns `Int` (count). `TransactionDao.insertBatchIgnore` already returns `LongArray` of row ids (-1 for ignored). Add `TransactionRepository.insertBatchReturningIds(transactions): List<Long>` (impl maps the `LongArray`); `SmsSyncUseCase` uses it so it can record labels per inserted row. Existing `insertBatch(): Int` stays for the CSV importer.
- **`AutoCategoryEngine`** (pure object, no DI, like ParserEngine):
  ```kotlin
  fun matchCategory(description: String, rules: List<UserCategoryRule>): Long?
  ```
  First rule whose `pattern` is a case-insensitive substring of `description` wins; returns its `categoryId` or null. Rules are expected pre-sorted by id (insertion order = priority).
- **Wiring in `SmsSyncUseCase`**:
  - `sync()`: load active rules once with banks/rules; for each parsed transaction, set `categoryId = autoCategoryEngine.matchCategory(description, rules)`. After `insertBatchReturningIds`, for each inserted row (id != -1) that matched a rule, record `TransactionLabel(transactionId = id, label = categoryName)`.
  - `handleIncomingSms()`: same engine application for the single incoming SMS; record label when the returned id is valid.
  - Requires `categoryRepository` injected (for rules + category names); `TransactionLabelDao` via a small repo/method.
- **Rule management UI**: out of scope for F4 (engine + wiring only). Rules are covered by unit tests and DAO integration tests; a Settings rule manager is future work.

### Tests
- `AutoCategoryEngineTest`: substring match, case-insensitivity, first-match priority, no match → null.
- `SmsSyncUseCaseTest`: transactions get `categoryId` set when a rule matches; `TransactionLabel` recorded; no match → null categoryId.
- `MigrationTest`: `migrate6To7`.
- (F8 covers DAO integration for the new DAOs.)

---

## F7 — Error components

### Current state
- No shared error UI. Sync failures surface as a snackbar message; no inline banner or dialog exists.

### Design
- New shared composables in `ui/components/`:
  - `ErrorBanner(message, onDismiss, modifier)` — inline `Surface` row: error icon + message + close icon.
  - `ErrorSnackbar` — thin wrapper around `SnackbarHostState.showSnackbar` helper (message + action label + duration) so callers don't repeat snackbar wiring.
  - `ErrorDialog(title, message, confirmText, onConfirm, onDismiss)` — `AlertDialog` wrapper.
- Wire `ErrorBanner` into the Transactions sync-failure path: when `syncMessage` indicates failure, show banner (replace/augment the snackbar).
- No new test dependencies (visual components; logic lives in VMs already covered).

### Tests
- Skip dedicated compose tests (consistent with existing non-tested visual components); covered by build + VM tests.

---

## F8 — Room DAO integration tests (androidTest)

### Current state
- `room-testing` already a dependency. No in-memory DAO tests exist (only migration tests).

### Design
- New `app/src/androidTest/java/com/smsexpensetracker/core/database/` test classes using `Room.inMemoryDatabaseBuilder(context, SmsExpenseDatabase::class.java)` (no migration) — schema comes from the current compiled entities.
  - `TransactionDaoTest`: insert/query/update/delete; batch insert (`insertBatch`) count; `smsBodyHash` dedup (same hash ignored via `@Insert(onConflict = IGNORE)`); update preserves hash.
  - `BankDaoTest`: CRUD.
  - `CategoryDaoTest`: CRUD + default flag.
  - `SmsRuleDaoTest`: insert + active-rule query.
  - `SyncMetaDaoTest`: upsert/read.
  - `UserCategoryRuleDaoTest` + `TransactionLabelDaoTest`: CRUD + FK cascade behavior.
- Uses `kotlinx-coroutines-test` `runTest` with suspend DAO calls.
- Note: in-memory DB is built from **current** entities (v7), so these tests validate the live schema and must be updated if entities change.

### Tests
- The androidTest classes themselves.

---

## Commit order & verification
1. F2 (VM + UI + unit tests) → `./gradlew testDebugUnitTest`
2. F3 (SyncRange, use case range, Settings UI + tests) → unit suite
3. F4 (entities, DAOs, v7 migration, engine, wiring, tests) → unit suite + `connectedDebugAndroidTest` (migration test) + commit `7.json`
4. F7 (components + banner wiring) → build
5. F8 (DAO androidTests) → `connectedDebugAndroidTest`
6. Update TODO.md (mark F2/F3/F4/F7/F8 complete, note F6/F9/F10 deferred), final full verification, one commit per feature.
