# SMS Sync Core — Design

> **Date:** 2026-08-01
> **Status:** Approved — ready for implementation plan
> **Scope:** Manual sync core only (TODO Tasks 6–7, first sub-project). Background Worker, live progress banner, and unparsed-list UI are separate later sub-projects.

---

## 1. Overview

Make the app read and parse real bank SMS instead of demo data. This increment delivers: a runtime `READ_SMS` permission flow, a schema change for deduplication, an `SmsSyncUseCase` that scans the inbox → parses → dedup-inserts → records parse logs, and a "Sync SMS" trigger on the Transactions empty state with a completion snackbar.

**Out of scope (later sub-projects):**
- `SmsSyncWorker` (WorkManager background sync) — TODO Task 7
- Live progress banner + unparsed SMS list UI — TODO Task 16
- First-launch onboarding permission screen — TODO Task 15
- "Add bank rules" UI — TODO Task 14 (existing note)

---

## 2. Decisions (from brainstorming)

| Question | Decision |
|---|---|
| Sync range | **Full scan + hash-dedup** every sync (not date-incremental). Idempotent; enables unparsed-retry. `SyncMeta.lastSmsId` incremental = later optimization. |
| Sender filtering | **Read all SMS, parse all.** Non-bank SMS simply don't match a rule → `ParseLog(FAILED)`. New banks work automatically once a rule is added. |
| Progress UI | Use case exposes `StateFlow<SyncProgress>` internally; this increment's UI shows only a spinner on the Sync button + completion snackbar with counts. |
| Permission UX | On-demand when user taps "Sync SMS": check → rationale dialog → request → on permanent deny, "Open Settings" snackbar. |
| Unparsed SMS | Recorded as `ParseLog(status = FAILED)` with raw body + sender + reason. Not silently dropped. Count surfaced in snackbar. View/list screen deferred to Task 16. |
| Use case style | Suspend function returning `SyncResult`, progress via exposed `StateFlow`. Matches existing codebase conventions. |

---

## 3. Architecture

### 3.1 Components

| Component | Layer | Responsibility |
|---|---|---|
| `PermissionManager` | `data/sms/` | Check / request `READ_SMS`, rationale decision, open app settings intent |
| `SmsSyncUseCase` | `domain/usecase/` | Orchestrates scan → parse → dedup insert → ParseLog → progress |
| `SyncProgress`, `SyncResult` | `domain/value/` | Live progress + final counts |
| `TransactionEntity` + `MIGRATION_2_3` | `core/database/` | Add `smsBodyHash` for dedup |
| `TransactionRepository.insertBatch` | `data/repository/` | Compute SHA-256 hash, bulk insert with `IGNORE`, return inserted count |
| UI wiring | `ui/screens/transactions/` | Sync button, permission flow, result snackbar |

### 3.2 Data flow

```
TransactionsScreen "Sync SMS" tap
  → PermissionManager.check()          (request + rationale if missing)
  → TransactionsViewModel.sync()       [viewModelScope, Dispatchers.IO]
      → SmsSyncUseCase.sync()
          → SmsReader.readSms()        (full inbox, no date filter)
          → for each msg: ParserEngine.parse(body, sender, rules)
              ├─ matched  → Transaction (hash in repo) → insertBatch(IGNORE)
              └─ no match → ParseLog(FAILED)
          → SyncMeta.upsert(lastSyncTimestamp = now)
          → emit progress → return SyncResult
  → snackbar: "Scanned X, added Y, unparsed Z"
```

---

## 4. Data layer

### 4.1 Migration 2→3

```sql
ALTER TABLE transactions ADD COLUMN smsBodyHash TEXT
CREATE UNIQUE INDEX index_transactions_smsBodyHash ON transactions(smsBodyHash)
```

- SQLite allows multiple `NULL`s in a unique index → existing rows and manual entries (hash `null`) are unaffected.
- Add `MIGRATION_2_3` to `SmsExpenseDatabase` and register via `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.
- Add an androidTest migration test for 2→3 preserving data and creating the index.

### 4.2 Entity

- `TransactionEntity`: add `val smsBodyHash: String? = null`.
- **Domain `Transaction` stays unchanged.** The hash is a storage-level dedup detail computed by the repository.

### 4.3 DAO

- Add `@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBatchIgnore(transactions: List<TransactionEntity>): LongArray`.
- Ignored duplicate rows return `-1`; inserted count = count of positive ids.
- Existing `insertAll` (REPLACE) and `insert` (REPLACE) remain for manual-entry flows.

### 4.4 Repository

- `TransactionRepository.insertBatch(transactions: List<Transaction>): Int`:
  - For each transaction, if `rawSms.isNotBlank()` compute `smsBodyHash = SHA-256(rawSms)` hex; else `null`.
  - Map to entities, call `insertBatchIgnore`, return count of positive ids.
- Hash is deterministic on `rawSms` → re-running the same body is a no-op.

---

## 5. `SmsSyncUseCase`

- `@Singleton`, `@Inject constructor` with:
  - `SmsReader` (wraps `ContentResolver`; provided via Hilt module)
  - `SmsRuleRepository`, `TransactionRepository`, `ParseLogRepository`, `SyncMetaRepository`
  - `CoroutineDispatcher` defaulting to `Dispatchers.IO`
- `val progress: StateFlow<SyncProgress>` — fields: `processed`, `total`, `unparsed`.
- `suspend fun sync(): SyncResult` (fields: `scanned`, `inserted`, `unparsed`, `error: String?`):
  1. Guard: if a sync is already running, return immediately.
  2. Load rules once: `SmsRuleRepository.getAllRules().first()`.
  3. `SmsReader.readSms().collect` — build message list; `total = size`.
  4. Chunk into batches of 100; per batch:
     - For each message: `ParserEngine.parse(body, sender, rules)`.
       - Matched → collect `Transaction(...)`.
       - No match (`errorMessage != null`) → `ParseLogRepository.insert(FAILED)` + increment unparsed.
     - `transactionRepository.insertBatch(chunk)` → accumulate `inserted`.
     - Emit progress.
  5. `SyncMetaRepository.upsert(lastSyncTimestamp = now)`.
  6. Return `SyncResult`.
- Wrap repo/parse work in `runCatching`-style try/catch; rethrow `CancellationException`; set `error` on failure.
- The `isSyncing` guard replaces the TODO's "debounce(300ms)" (protects against double-tap/concurrent runs).

**Transaction construction from `ParsedResult`:**
```
Transaction(
  id = 0, bankId = result.bankId!!,      // only matched results reach here
  amount = result.amount, type = result.type, description = result.description,
  transactionDate = LocalDate.now().atStartOfDay(),
  categoryId = null, rawSms = msg.body, smsTimestamp = msg.timestamp,
  createdAt = LocalDateTime.now(), parseMethod = ParseMethod.SMS
)
```

**ParseLog construction on failure:**
```
ParseLog(smsBody = msg.body, smsSender = msg.sender,
         parsedAt = now, status = FAILED, errorMessage = result.errorMessage)
```

---

## 6. `PermissionManager`

- Lives in `data/sms/`.
- `fun hasPermission(context: Context): Boolean` — `ContextCompat.checkSelfPermission(READ_SMS) == GRANTED`.
- `fun shouldShowRationale(activity: Activity): Boolean` — `shouldShowRequestPermissionRationale`.
- `fun openSettings(context: Context)` — `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, package uri)`.
- The caller drives the actual system request via `rememberLauncherForActivityResult(RequestPermission)` (UI-layer). `PermissionManager` stays testable without Android mocks where possible.
- **Rationale flow (in Transactions screen):** tap Sync → no permission → if `shouldShowRationale`, show `AlertDialog` ("Allow SMS access to read bank transaction alerts?") → request; on deny twice, snackbar with "Open Settings" action.

---

## 7. UI integration (Transactions screen)

- Wire the existing empty-state **"Sync SMS"** button (`TransactionsScreen.kt`) to `viewModel.sync()`.
- While `isSyncing`: button shows `CircularProgressIndicator`, disabled.
- On completion: snackbar `"Scanned X, added Y, unparsed Z"`.
- List/dashboard refresh automatically via existing Room `Flow` subscriptions.
- Manual-entry FAB unchanged.
- `TransactionsViewModel` gains: `sync()`, `isSyncing` in state, snackbar event, and a permission-request hook.

---

## 8. Error handling

| Case | Behavior |
|---|---|
| Parse failure | Counted + `ParseLog(FAILED)`; never crashes, never silently dropped |
| DB / repository error | Caught in use case → `SyncResult.error`; snackbar "Sync failed, try again" |
| Permission denied | Abort sync; snackbar with "Open Settings" action |
| Cancellation | `CancellationException` rethrown |

---

## 9. Testing

- **`SmsSyncUseCaseTest`** (MockK + `runTest`, `StandardTestDispatcher`):
  - Full scan parses a real HDFC SMS into a transaction (`ParserEngine` real).
  - Unparsed SMS → `ParseLog(FAILED)` inserted + counted.
  - Dedup: second sync with identical bodies inserts 0 (`insertBatch` mocked to return inserted count; verify hash-based IGNORE at DAO level).
  - `rawSms = ""` → `smsBodyHash = null` (repository unit test).
  - Progress emits `processed`/`total`.
  - Concurrent sync guard (second call no-ops).
  - Repo failure → `SyncResult.error` set.
- **`TransactionRepositoryImplTest`** additions: `insertBatch` hashes non-blank bodies, returns inserted count, leaves `smsBodyHash = null` for blank bodies.
- **Migration androidTest**: 2→3 preserves rows and creates the unique index.
- **Gate:** `./gradlew testDebugUnitTest assembleDebug` green; existing 40+ tests stay green.

---

## 10. Deliverables (files touched)

- `core/database/entity/TransactionEntity.kt` — add `smsBodyHash`
- `core/database/SmsExpenseDatabase.kt` — `MIGRATION_2_3`, register migrations
- `core/database/dao/TransactionDao.kt` — `insertBatchIgnore`
- `domain/repository/TransactionRepository.kt` + `data/repository/TransactionRepositoryImpl.kt` — `insertBatch`
- `domain/value/SyncProgress.kt`, `domain/value/SyncResult.kt` — new
- `domain/usecase/SmsSyncUseCase.kt` — new; **delete the unused `SyncSmsUseCase.kt` stub** (it's referenced nowhere) to avoid two confusingly-similar classes
- `data/sms/PermissionManager.kt` — new
- `data/sms/SmsReader.kt` + `di/` — add a Hilt module that provides `SmsReader(ContentResolver)` and binds `SyncMetaRepository`/`ParseLogRepository` (check `RepositoryModule` / `DatabaseModule`; bind whichever is missing)
- `ui/screens/transactions/TransactionsViewModel.kt` + `TransactionsScreen.kt` — sync trigger, permission flow, snackbar
- Tests: `SmsSyncUseCaseTest`, `TransactionRepositoryImplTest` additions, `MigrationTest` 2→3
