# On-Arrival SMS Capture Design

**Date:** 2026-08-05
**Status:** Draft (awaiting human review)

## 1. Goal

When a new bank SMS arrives, parse and record it as a transaction **automatically** — no manual "Sync SMS" tap required. Uses the already-declared `RECEIVE_SMS` permission via a manifest `BroadcastReceiver`. This is the instant, event-driven half of background capture; the periodic catch-up worker (`SmsSyncWorker`) remains deferred under TODO **F10**.

Battery impact is negligible: the receiver is dormant until an SMS arrives, piggybacks on the already-wakeful SMS delivery window, does one `<50ms` parse + one DB insert, and holds no wakelocks/timers.

## 2. Scope

**In scope:**
- `SmsIncomingReceiver` (`BroadcastReceiver` for `Telephony.Sms.Intents.SMS_RECEIVED_ACTION`) + manifest registration.
- New `SmsSyncUseCase.handleIncomingSms(body, sender, timestamp)` single-message entry point, sharing parse/log logic with the existing full-scan `sync()`.
- App-scoped `CoroutineScope` in DI so the receiver can do async work safely.
- Runtime permission flow upgraded to request **both** `READ_SMS` and `RECEIVE_SMS` (currently only `READ_SMS` — a pre-existing gap).
- Unit tests for `handleIncomingSms`.

**Out of scope (explicit):**
- Periodic `SmsSyncWorker` / incremental sync (stays **F10**, deferred).
- Notifications when a new transaction is recorded.
- A "missed N transactions while demo data was loaded" badge (demo-data gate skips silently).
- Changing full-scan `sync()` behavior for non-bank senders (it logs every non-match as FAILED today; unchanged here — see §5 divergence note).
- Unit-testing the receiver itself (android-bound; kept a thin shell, covered by manual device test).

## 3. Architecture

```
SMS arrives (radio wakes phone)
  → SMS_RECEIVED_ACTION broadcast → SmsIncomingReceiver.onReceive()
  → Telephony.Sms.Intents.getMessagesFromIntent() → body / sender / timestamp
  → goAsync(); appScope.launch { SmsSyncUseCase.handleIncomingSms(...) }
       → demo data loaded?            → silent return (false)
       → detectBankForSender() == null? → silent return (false)   [no ParseLog]
       → ParserEngine.parse(body, sender, rules)
       → parse failed                 → ParseLog(FAILED) + return (false)
       → parse ok (amount > 0)        → insertBatch([Transaction])  [dedup via smsBodyHash]
                                      → SyncMeta.lastSyncTimestamp bump
       → finally { pendingResult.finish() }
```

The full-scan `sync()` and the new single-message path share one classify-and-record helper so their parse/FAILED-log logic cannot drift.

## 4. Components

### 4.1 `SmsIncomingReceiver` (new, `data/sms/SmsIncomingReceiver.kt`)

- `@AndroidEntryPoint` `BroadcastReceiver`; injects `SmsSyncUseCase` and the app `CoroutineScope`.
- `onReceive`: ignore unless `intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION`; `getMessagesFromIntent(intent)` → concatenate `messageBody` across PDUs (multipart), first `originatingAddress`, first `timestampMillis`; blank body → return.
- `goAsync()` → `appScope.launch { try { useCase.handleIncomingSms(...) } finally { pendingResult.finish() } }`.
- No parsing/business logic in the receiver — pure delegation.

Manifest (`AndroidManifest.xml`, inside `<application>`):

```xml
<receiver
    android:name=".data.sms.SmsIncomingReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.provider.Telephony.SMS_RECEIVED_ACTION" />
    </intent-filter>
</receiver>
```

`exported="true"` is required: the system (not the app) delivers this broadcast. `SMS_RECEIVED_ACTION` is exempt from Android 8+ implicit-broadcast restrictions, so a manifest receiver works.

### 4.2 App-scoped `CoroutineScope` (DI)

Add to a DI module (e.g. `SmsModule`):

```kotlin
@Provides @Singleton
fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

Receivers outlive their `onReceive`, so the receiver must not use a receiver-owned scope — it needs the application scope.

### 4.3 `SmsSyncUseCase.handleIncomingSms`

New public method (constructor gains `BankRepository` for `detectBankForSender`):

```kotlin
suspend fun handleIncomingSms(body: String, sender: String, timestamp: Long): Boolean
```

Steps:
1. `if (demoDataPreferences.demoDataLoaded.first()) return false` (silent gate, same as `sync()`).
2. `val banks = bankRepository.getAllBanks().first()`; `if (detectBankForSender(sender, banks) == null) return false` — non-bank SMS dropped with **no ParseLog** (keeps the Unparsed SMS screen meaningful; see §5).
3. Load active rules (`smsRuleRepository.getAllRules().first().filter { it.isActive }`) and run the shared classify-and-record helper (same as `sync()`).
4. On success build `Transaction(parseMethod = SMS)` and `insertBatch(listOf(tx))`. Dedup is DB-enforced via the unique `smsBodyHash` index (`@Insert(onConflict = IGNORE)`), so re-parsing the same body is idempotent.
5. `syncMetaRepository.upsert(SyncMeta(lastSyncTimestamp = now, lastSmsId = null))`.
6. Return `true` if a transaction was inserted, else `false` (false also feeds a future "missed" counter).

**Shared-helper refactor:** extract the parse → FAILED-log → Transaction construction block currently inlined in `sync()` (SmsSyncUseCase.kt:63-101) into a private suspend helper used by both paths. Invariant: `sync()`'s observable behavior and all existing `SmsSyncUseCaseTest` assertions stay identical.

### 4.4 Permission flow (`READ_SMS` + `RECEIVE_SMS`)

- `TransactionsScreen.kt:83-110`: replace `ActivityResultContracts.RequestPermission()` with `RequestMultiplePermissions()` requesting `arrayOf(READ_SMS, RECEIVE_SMS)`; only call `viewModel.sync()` when both are granted; otherwise the existing "Open Settings" snackbar path.
- `PermissionManager.hasPermission(context)` checks both permissions.
- No manifest permission changes — both are already declared.
- The rationale path (`shouldShowRationale`) is unchanged.

## 5. Sender bail + ParseLog hygiene

`detectBankForSender(sender, banks)` already exists (`core/parser`, used by `UnparsedSmsViewModel`). On-arrival uses it to drop non-bank SMS silently. Otherwise every OTP/courier SMS would create a FAILED ParseLog and flood the Unparsed SMS screen.

**Divergence note:** full-scan `sync()` still records FAILED for every non-matching message in the inbox (pre-existing behavior, untouched here). A future cleanup could apply the same sender-bail in `sync()`; deliberately out of scope to avoid changing existing sync behavior/tests.

## 6. Demo-Data Gate

If demo data is loaded, `handleIncomingSms` returns `false` without writing anything — same gate as `sync()`. There is no UI to surface a notice from a background receiver, so the skip is silent (a "missed transactions" badge is a future option).

## 7. Error Handling

- `pendingResult.finish()` is always called (`finally`) so the system doesn't consider the broadcast hung.
- `CancellationException` re-thrown (existing convention).
- Any other exception inside `handleIncomingSms` is caught, logged via `Timber` (`PARSE` tag, matching `sync()`), and the method returns `false` — a background receiver must never crash the process.
- Idempotency: even if the same SMS is parsed again (on-arrival + later full-scan), the unique `smsBodyHash` index prevents duplicates.

## 8. Testing

TDD-first, matching existing styles (JUnit 4, MockK, `runTest`). Constructor change means existing `SmsSyncUseCaseTest` gains a mocked `BankRepository`.

**`SmsSyncUseCaseTest` additions (~7):**
1. demo data loaded → returns `false`, no repo calls.
2. non-bank sender → returns `false`, no ParseLog insert, no transaction insert.
3. bank sender, parse success → `insertBatch` called with correct Transaction fields; `SyncMeta` upserted; returns `true`; no FAILED log.
4. bank sender, parse failure → FAILED ParseLog recorded; no `insertBatch`; returns `false`.
5. exception inside → returns `false`, no crash (re-throws `CancellationException`).
6. existing `sync()` tests unchanged and still green (refactor is behavior-preserving).

**Receiver:** no unit test (android-bound, thin shell). Manual device/emulator check (folded into **F1**): `adb emu sms send <number> "<bank sms text>"` with the app closed → reopen → transaction present without any manual sync.

**Gate:** `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`.

## 9. Files

- New: `app/src/main/java/com/smsexpensetracker/data/sms/SmsIncomingReceiver.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt` (`handleIncomingSms` + shared helper + `BankRepository` dep)
- Modify: `app/src/main/java/com/smsexpensetracker/di/SmsModule.kt` (app `CoroutineScope`)
- Modify: `app/src/main/java/com/smsexpensetracker/AndroidManifest.xml` (receiver)
- Modify: `app/src/main/java/com/smsexpensetracker/data/sms/PermissionManager.kt` + `ui/screens/transactions/TransactionsScreen.kt` (both permissions)
- Tests: `app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt`
- Docs: `TODO.md` (add **F11**, mark on completion)

## 10. Track Placement & Follow-ups

- New **F11 — On-arrival SMS capture (instant)** — added to the Finalization Track (P1).
- **Failed on-arrival messages are re-attempted for free:** `sync()` is a full inbox re-scan, so any later "Sync SMS" tap (or Unparsed SMS → "Re-sync now", which clears stale FAILED logs first) re-reads and re-parses bodies that previously failed — no extra code needed. On-arrival is the fast path; full-scan is the safety net.
- Periodic `SmsSyncWorker` catch-up remains **F10** (deferred).
