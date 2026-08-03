# Unparsed SMS Review Screen — Design

**Date:** 2026-08-03
**Status:** Approved by user

## 1. Problem

When a sync fails to parse an SMS, the full SMS body is stored in the `parse_logs` table (`SmsSyncUseCase.kt:70-79` via `ParseLogRepositoryImpl.insert`), but **no UI displays it**. The Logs screen (`LogViewerScreen.kt`) shows only sender, status, timestamp, and error — never the body. A user who sees "Scanned 29, unparsed 6" has no way to read *what* those 6 SMS said, so they cannot fix the rules.

## 2. Goal

A dedicated **Unparsed SMS** review screen where the user can:
1. See the full body of every SMS that failed to parse (deduplicated by body).
2. Fix a failed SMS by creating a matching rule in the existing Rule Editor, pre-filled with the SMS body and the auto-detected bank.
3. Re-run the sync from the screen and see the freshly-updated result.

## 3. Scope

- **In:** new review screen + ViewModel + tests; `ParseLogDao`/`ParseLogRepository` additions; Rule Editor `sampleSms` prefill; extracted sender→bank detection shared with Parser; Settings row + navigation; clear-failed-logs-on-resync.
- **Out:** no changes to `SmsSyncUseCase`, `ParserEngine`, `RegexParser`, `TemplateCompiler`, `TypeInferrer`, `ConfidenceScorer`, `SenderDetector` matching semantics. No changes to the existing Logs screen. No inline rule editor (reuse Rule Editor). No auto re-sync on rule save.

## 4. Components

### 4.1 Navigation

- Settings row **"Unparsed SMS"** placed directly above the existing "Logs" row (`SettingsScreen.kt`, Data section). Navigates to `"unparsed_sms"`.
- New route `composable("unparsed_sms")` in `NavGraph.kt` → `UnparsedSmsScreen(onBack, onFix = { bankId, smsBody -> navController.navigate("banks/$bankId/rules/edit?sampleSms=${Uri.encode(smsBody)}") })`.
- `RuleEditorScreen` untouched as a composable; the route gains an optional `sampleSms` arg (see 4.4).

### 4.2 Data layer

- `ParseLogDao` gains one new member:
  - `@Query("DELETE FROM parse_logs WHERE status = 'FAILED'") suspend fun deleteFailed()`
- `ParseLogRepository` interface + `ParseLogRepositoryImpl` expose `suspend fun deleteFailed()`.
- The existing `getAllLogs()` (`ParseLogDao.getAllLogs`, `ORDER BY parsedAt DESC`) is the **single** data source for the review screen; the All/Failed toggle filters in memory (see 4.3). No `getFailedLogs()` query is added — filtering a few hundred in-memory rows is simpler and avoids a second Flow source.
- No schema change — `parse_logs` table already stores `smsBody`, `smsSender`, `parsedAt`, `status`, `errorMessage` (`ParseLogEntity`).

### 4.3 UnparsedSmsViewModel

State:
- `parseLogs: StateFlow<List<ParseLog>>` — raw rows from `getAllLogs()`, the single source of truth.
- `failedLogs: StateFlow<List<FailedSms>>` — derived from `parseLogs` filtered to FAILED and deduped by body, where `FailedSms(smsBody, smsSender, errorMessage, lastParsedAt, failCount, bankId?)`.
- `filter: StateFlow<UnparsedFilter>` with `enum UnparsedFilter { FAILED, ALL }` — default FAILED. The UI toggle re-derives the displayed list from `parseLogs` without re-querying. (Today only FAILED rows exist since sync only inserts failures; the "All" option is future-proofing and user-requested.)
- `banks: StateFlow<List<Bank>>`
- `isSyncing: StateFlow<Boolean>`, `syncMessage: StateFlow<String?>`

Logic:
- **Derivation:** when filter = FAILED, display `failedLogs` (deduped); when filter = ALL, display `parseLogs` raw rows. Dedup groups FAILED rows by `smsBody`; each unique body becomes one `FailedSms` with `failCount = group size` and `lastParsedAt = max(parsedAt)`.
- **Bank detection:** reuse extracted `detectBank(sender, banks)` (see 4.5). `bankId` is `null` when no bank matches; UI disables "Fix" and shows "No matching bank — add it in Banks & Rules first."
- **Re-sync:** `fun resync()` — guard on `isSyncing`; then `parseLogDao.deleteFailed()` (via repository) **then** `smsSyncUseCase.sync()`; set `syncMessage` from `SyncResult` (`error != null` → "Sync failed. Try again.", else "Scanned X, added Y, unparsed Z"). Because `getFailedLogs()` is a Flow, the list auto-refreshes after delete+sync.
- `consumeSyncMessage()` clears the snackbar message.

### 4.4 Rule Editor `sampleSms` prefill

- `NavGraph.kt` route `banks/{bankId}/rules/edit?ruleId={ruleId}&sampleSms={sampleSms}` — `sampleSms` optional `String` default `""` (URI-encoded at navigation time).
- `RuleEditorViewModel`: in `init`, if `savedStateHandle.get<String>("sampleSms")` is non-blank, set `sampleSms` in `_uiState`. Existing prefill of `draftPattern`/`description` for `ruleId` unchanged. The screen's "Sample SMS" field is simply pre-filled; user tests and saves as usual.

### 4.5 Shared sender→bank detection

Extract the body of `ParserViewModel.detectBank` (`ParserViewModel.kt:75-82`) into a pure top-level function so both screens share it. Recommended home: `core/parser/SenderDetector.kt` (it already owns `cleanTraiPrefix`):

```kotlin
fun detectBankForSender(sender: String, banks: List<Bank>): Long? {
    val cleaned = SenderDetector.detect(sender).value.uppercase()
    if (cleaned.isBlank()) return null
    return banks.firstOrNull { bank ->
        val smsSender = bank.smsSender.uppercase()
        cleaned == smsSender || cleaned.contains(smsSender) || smsSender.contains(cleaned)
    }?.id
}
```

`ParserViewModel.detectBank` becomes a thin delegate to this function. (`SenderDetector.kt` is in `core/parser`, which currently imports `domain.model.SenderId` — `Bank` is also a domain model, so the import is consistent.)

### 4.6 UnparsedSmsScreen UI

- Scaffold + TopAppBar "Unparsed SMS" with back arrow.
- Filter row: segmented toggle **All | Failed**, default Failed.
- "Re-sync now" button (top, under the filter) with busy state "Syncing…" while `isSyncing`.
- `LazyColumn` of cards, one per `FailedSms` (dedup) when filter = FAILED; when filter = ALL, one card per raw row with same layout but no dedup count.
  - Header: sender (bold), bank name (if any), red "FAILED" badge.
  - Body: full `smsBody`, monospace, wrap (this is the key fix — bodies visible).
  - Meta: "Failed N×" + last attempt time; `errorMessage` in muted text.
  - "Fix" button → `onFix(bankId, smsBody)`; disabled when `bankId == null` (with the hint below).
  - When `bankId == null`, show hint text "No matching bank — add it in Banks & Rules first."
- Empty state: "No unparsed SMS" text when FAILED filter yields nothing.
- Snackbar for `syncMessage` (pattern copied from Transactions/Logs screens).

## 5. Behavior changes

- Settings shows "Unparsed SMS" above "Logs".
- A failed sync's SMS bodies are now readable and fixable without leaving the app.
- "Re-sync now" clears stale FAILED parse logs before re-running sync, so the list reflects only genuinely-still-failing SMS.
- Existing Logs screen unchanged.

## 6. Error handling

- Re-sync failure → snackbar "Sync failed. Try again." (matches `TransactionsViewModel.sync`).
- No matching bank for a sender → "Fix" disabled + hint text; user creates the bank/rules manually in Banks & Rules.
- `getAllLogs()` is a Flow — DB errors surface as exceptions on collection; no special handling (consistent with the rest of the app's Flow usage).

## 7. Known risks / decisions

- **Clear-on-resync (user-approved):** "Re-sync now" deletes ALL FAILED parse logs first. Trade-off accepted: old failure history is not preserved across re-syncs; only current failures remain. This makes the list self-correcting after rules are fixed.
- **"All" filter shows only FAILED today** because sync never inserts SUCCESS/SKIPPED rows. The filter is a UI affordance for future use, not a behavioral difference today.
- **Fresh-install-only seeds** (from the prior feature) mean some SMS on this emulator may fail for formats not in the seeded rules — exactly what this screen exists to surface.
- `sampleSms` passed via nav arg is URI-encoded; very long SMS bodies are the expected case (bank SMS < 500 chars), acceptable for nav args.

## 8. Acceptance criteria

1. Fresh sync that produces unparsed SMS → Settings → "Unparsed SMS" lists each unique failing SMS body with sender, bank (if detectable), FAILED badge, "Failed N×", and last attempt time.
2. Tapping "Fix" opens the Rule Editor with the Sample SMS field pre-filled and the bank selected; writing a matching template, testing, and saving creates the rule.
3. Back on the review screen, "Re-sync now" re-runs sync, clears old FAILED rows, and the fixed SMS no longer appears.
4. An SMS whose sender matches no bank shows "Fix" disabled with the hint.
5. `./gradlew testDebugUnitTest assembleDebug` green.
