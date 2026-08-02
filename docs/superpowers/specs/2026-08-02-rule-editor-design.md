# Rule Editor Screen — Design Spec

**Date:** 2026-08-02
**Status:** Approved
**Task reference:** Follow-up to TODO.md Task 14 (Settings Screen) — SMS rule management UX improvement

## 1. Overview

Creating SMS parsing rules currently requires typing a raw regex into a small dialog (`RuleDialog.kt`) with no way to verify the pattern against a real SMS before saving. The parser contract — group 1 = amount, group 2 = description (`RegexParser.kt:11`) — is invisible to the user, so rules are frequently saved that silently never match.

This feature replaces the add/edit rule dialog with a **full-screen editor laid out like the existing Parser Test screen**. The user pastes a real SMS, writes the pattern, and taps **Test** to see the actual parse result (extracted amount + description) before saving. The same `RegexParser.parse` engine that runs against real SMS powers the preview, so "what you see is what will be stored."

## 2. Goals

- Replace `RuleDialog.kt` for both add and edit paths.
- Provide a Parser-Test-style full-screen editor with a paste-able sample SMS.
- "Test" button runs the real `RegexParser.parse(sampleSms, draftPattern, bankId)` and shows extracted amount + description or a clear no-match state.
- Live regex syntax validation (existing `validatePattern`) as the user types; match result only on button tap.
- Inline examples + group-convention helper (group 1 = amount, group 2 = description).
- **Save requires a successful test** — a rule cannot be saved until its pattern has matched the sample SMS at least once.
- Editing an existing rule pre-fills the fields from the saved rule.

## 3. Non-Goals

- Auto-generating regex from a pasted SMS.
- Picking a sample SMS from saved transactions (`rawSms`) — manual paste only (upgrade path exists later).
- A guided no-regex pattern builder.
- Changing the parser contract or `RegexParser`/`ParserEngine` behavior.
- Live as-you-type matching (debounced) — match runs only on button tap.

## 4. Screen: `RuleEditorScreen.kt`

Layout (top to bottom):

```
┌─────────────────────────────────────────┐
│ ← Back                    Add rule      │  TopAppBar; right action "Save" (TextButton)
│                                          │
│  Bank: HDFC Bank           (read-only)   │  from bank flow, no selector
│                                          │
│  Sample SMS   (OutlinedTextField)        │  multiline (min 4, max 8 lines), monospace,
│                                          │  supportingText "Paste a real bank SMS"
│  Pattern (regex)  (OutlinedTextField)    │  multiline (min 3 lines), monospace,
│                                          │  live syntax error via validatePattern
│  "Group 1 = amount, Group 2 = description"
│  [▸ How it works & examples]             │  collapsible (ExpAnimatedVisibility toggle)
│                                          │  - convention reminder
│                                          │  - copyable example patterns:
│                                          │    UPI debit / card spend / UPI credit
│                                          │    (same seeds as SeedDatabaseCallback)
│  Description   (OutlinedTextField)       │  single line, ≤60 chars, validateRuleDescription
│                                          │  (the rule's label, e.g. "HDFC CC Debit")
│  [ Test ]                                │  Button, enabled when sample non-blank +
│                                          │  pattern syntactically valid
│  Result card                             │  match: green card "✓ ₹1,250 · Coffee Shop"
│                                          │  (Amount + Description fields, ResultField style)
│                                          │  no-match: red error-container card
│                                          │  "No match for this SMS"
└─────────────────────────────────────────┘
```

Notes:
- Save (TopAppBar action) enabled only when: `descriptionError == null && patternError == null && testResult is a match`.
- On save success → screen calls `onSaved()` → `navController.popBackStack()` → BankDetail rules flow refreshes automatically (live flow).
- No bottom navigation bar on this screen (it's a nested destination pushed over the bank detail).

## 5. ViewModel: `RuleEditorViewModel.kt`

- `@HiltViewModel`, injects `BankRepository` + `SmsRuleRepository`. Reads `bankId: Long` (required) and `ruleId: Long?` (optional) from `SavedStateHandle` (mirrors `BankDetailViewModel`).
- `val bank: StateFlow<Bank?>` — `getBankById(bankId)`, `WhileSubscribed(5000)`.
- Mutable editor state in a `RuleEditorUiState` data class:
  - `sampleSms: String`, `draftPattern: String`, `description: String`
  - `testResult: RegexMatch?` (null = not yet tested / no match) — reuse the existing `RegexMatch` from `RegexParser.kt` rather than a new type.
  - `saved: Boolean`
  - `saveError: String?`
- **Pre-fill**: on init, if `ruleId != null`, load `getRuleById(ruleId)` and populate `draftPattern`/`description` (id=0 fallback if missing).
- `onSampleSmsChange / onPatternChange / onDescriptionChange` — update state; changing sample or pattern clears `testResult`.
- `onTest()` — synchronous pure call `RegexParser.parse(sampleSms, draftPattern, bankId)` → sets `testResult`.
- `onSave()` — `viewModelScope.launch`:
  - add path: `smsRuleRepository.insert(SmsRule(0, bankId, pattern.trim(), description.trim()))`
  - edit path: `smsRuleRepository.update(existing.copy(pattern = ..., description = ...))`
  - set `saved = true`; on `CancellationException` rethrow; on other `Exception` set `saveError = "Could not save rule. Please try again."`
- `consumeSaveError()` — clear snackbar flag.

## 6. Navigation

`NavGraph.kt` — single route with optional arg:

```kotlin
composable(
    route = "banks/{bankId}/rules/edit?ruleId={ruleId}",
    arguments = listOf(
        navArgument("bankId") { type = NavType.LongType },
        navArgument("ruleId") { type = NavType.LongType; defaultValue = -1L }
    )
) {
    RuleEditorScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
}
```

- `defaultValue = -1L` signals "add" (`ruleId == -1L` → null in VM).
- `BankDetailScreen`: remove `RuleDialog` usage; add callbacks `onAddRule` (FAB → navigate without `ruleId`) and `onEditRule(ruleId: Long)` (pencil → navigate with `ruleId`), wired in NavGraph.
- Delete dialog (`RuleDeleteDialog`) stays in `BankDetailScreen` unchanged.

## 7. Existing Code Changes

- **Delete** `ui/screens/banks/RuleDialog.kt`.
- **Modify** `BankDetailScreen.kt`: drop `showAdd`/`editing` rule-dialog state; FAB calls `onAddRule()`; pencil calls `onEditRule(rule.id)`.
- **Modify** `NavGraph.kt`: add route; pass new callbacks to `BankDetailScreen`.
- **Unchanged**: `RegexParser`, `ParserEngine`, `BankRulesValidation`, repositories, DAOs, schema, seed data.

## 8. Error Handling

- No-match → red card "No match for this SMS" (covers both non-matching text and group 1 not being a parseable amount — `RegexParser.parse` returns null for both).
- Test button disabled when sample blank or syntax invalid (no error to show).
- Repo write failure → Snackbar "Could not save rule. Please try again." via `saveError` + `consumeSaveError()`.
- Edit of a deleted rule → pre-fill yields empty fields; Save then inserts as a new rule (acceptable, no crash).

## 9. Testing

- `RuleEditorViewModelTest` (MockK + `runTest`, mirrors `BankDetailViewModelTest` conventions):
  - add mode (ruleId = -1): empty pre-fill, bank flow emitted.
  - edit mode (ruleId = N): pre-fills pattern + description from `getRuleById` (asserts `isActive` too — closes the deferred gap noted in the bank-rule final review).
  - `onTest` with matching pattern → `testResult` non-null with parsed amount/description.
  - `onTest` with non-matching pattern → `testResult` null.
  - `onTest` with pattern whose group 1 isn't a valid amount → `testResult` null (contract enforcement).
  - save gating: save writes `insert` (add) vs `update` (edit) with trimmed values.
  - save failure → `saveError` set.
- No new repo tests (thin delegation, unchanged).
- `RuleDialog` has no tests today (UI-only, no logic) — removing it removes nothing.

## 10. Files

**New:**
- `ui/screens/banks/RuleEditorScreen.kt`
- `ui/screens/banks/RuleEditorViewModel.kt`
- `app/src/test/.../ui/screens/banks/RuleEditorViewModelTest.kt`

**Deleted:**
- `ui/screens/banks/RuleDialog.kt`

**Modified:**
- `ui/screens/banks/BankDetailScreen.kt`
- `ui/navigation/NavGraph.kt`

## 11. Verification

- `./gradlew testDebugUnitTest assembleDebug` — all tests pass, build succeeds.
- Manual smoke: add rule with a real HDFC SMS → Test shows amount/description → Save → appears in bank detail; edit existing rule → fields pre-filled; pattern that doesn't match → red card, Save disabled.
