# Edit Transaction (Bottom-Sheet Editor) Design

**Date:** 2026-08-05
**Status:** Approved (sections 1–4 reviewed by human)

## 1. Goal

Tapping a transaction row in the Transactions list currently opens a read-only `TransactionDetailSheet` (only the category dropdown is editable). This feature makes the sheet a full editor so users can **manually correct** parsed transactions: amount, type (Credit/Debit), date, bank, description, and category. This closes the known gap from TODO Task 11 ("edit label" was originally planned but never landed).

## 2. Scope

**In scope:**
- Editing **amount, type, date, bank, description, category** on any transaction (SMS-derived or manual).
- A pure validation helper, a targeted DB update that preserves dedup integrity, live list refresh after update, and the demo-data gate.
- Update/Cancel CTAs; Update disabled while saving.

**Out of scope (explicit):**
- Transaction **delete** (not requested; Update/Cancel only).
- Editing `rawSms`, `smsTimestamp`, `createdAt`, `parseMethod`, `smsBodyHash` — read-only audit fields.
- Bulk editing, undo/history, edit-version tracking.

## 3. Architecture

Same chain as today: `TransactionsScreen` → `TransactionsViewModel` → `TransactionRepository` → `TransactionDao`. No new screens, no navigation changes.

```
Tap row → sheet opens with editable fields
   → user edits Amount / Type / Date / Bank / Description / Category
   → Update → ViewModel.validate() → repository.updateEditedTransaction(...)
   → Room targeted @Query UPDATE (editable columns only)
   → Room Flow re-emits → TransactionsViewModel.combine() rebuilds uiState
   → list + sheet refresh live automatically
```

**Correctness invariant:** the update uses a **targeted** `@Query` that touches only the six editable columns. A generic `@Update(entity)` must NOT be used here: `Transaction.toEntity()` drops `smsBodyHash`, so a full entity update would null the dedup hash and the next SMS sync would re-insert the same SMS as a duplicate.

## 4. Components

### 4.1 `TransactionEditSheet` (evolves `TransactionDetailSheet.kt`)

Same file, same `ModalBottomSheet` (`@OptIn(ExperimentalMaterial3Api::class)`, `skipPartiallyExpanded = true`).

Header: "Edit Transaction" (`titleLarge`).

Fields (reuse `ManualEntryScreen` patterns: `RoundedCornerShape(28.dp)`, `KeyboardType.Decimal` for amount, `SingleChoiceSegmentedButtonRow` for type, `ExposedDropdownMenuBox` for bank/category, `DatePickerDialog` for date):

| Field | Control | Validation |
|---|---|---|
| Amount | `OutlinedTextField`, `₹` leading icon, digit/`.`/`,` filter | non-blank; `parsePaisa` succeeds; > 0 |
| Type | `SingleChoiceSegmentedButtonRow` (Credit/Debit) | n/a |
| Date | read-only field + `DatePickerDialog` | n/a |
| Bank | `ExposedDropdownMenuBox` over `banks` | bank required (defaults to current) |
| Description | `OutlinedTextField`, single line | non-blank; ≤ 200 chars |
| Category | `ExposedDropdownMenuBox` with "None" + all categories | n/a (nullable) |

CTAs: **Cancel** (`onDismiss`, discard edits) and **Update** (validate → save → close). Update disabled while saving or while validation errors exist. On success a snackbar "Transaction updated" is shown.

### 4.2 `TransactionsViewModel`

- Edit form state initialized from the tapped transaction in `onTransactionClick`.
- Setters per field (`onAmountChange`, `onTypeChange`, `onDateChange`, `onBankChange`, `onDescriptionChange`, `onCategoryChange` for the form).
- Pure helper `validateTransactionEdit(amountInput: String, description: String): EditFormErrors` (mirrors `validateCategoryName` placement — lives next to the form state, unit-testable).
- `updateTransaction()`: gate on demo data → validate → `repository.updateEditedTransaction(...)` → refresh `_selectedTransaction` with the saved value → snackbar → dismiss sheet.
- Existing `onCategoryChange` in `TransactionsViewModel` (calling `updateTransactionCategory`) is superseded by the form — the detail sheet is its only caller. **`updateTransactionCategory` itself stays**: `CategorizeViewModel` (categorize tab) still calls it, so DAO/repository/interface keep the method. Only the detail-sheet `onCategoryChange` wiring and its `TransactionsViewModelTest` test are replaced.

### 4.3 Repository / DAO

New interface method:

```kotlin
suspend fun updateEditedTransaction(transaction: Transaction)
```

`TransactionRepositoryImpl` maps the domain `Transaction` to entity (enums via the existing `valueOf` pattern) and calls the new targeted DAO query:

```sql
@Query("""
    UPDATE transactions
    SET bankId = :bankId, amount = :amount, type = :type,
        description = :description, transactionDate = :transactionDate,
        categoryId = :categoryId
    WHERE id = :id
""")
suspend fun updateTransactionFields(
    id: Long, bankId: Long, amount: Long, type: TransactionType,
    description: String, transactionDate: LocalDateTime, categoryId: Long?
)
```

`type` uses the **entity** enum in the DAO signature; mapping from the domain enum happens in the repository impl. Untouched columns (`smsBodyHash`, `rawSms`, `smsTimestamp`, `createdAt`, `parseMethod`) preserve their stored values.

## 5. Demo-Data Gate

Mirror the existing pattern used by ManualEntry save / Parser add-as-transaction / CSV import / sync: when `demoDataLoaded` is true, tapping **Update** shows `DemoDataBarrierDialog` and writes nothing. Settings → Delete demo data unblocks editing.

## 6. Error Handling

- Validation errors render inline via `supportingText` (same as ManualEntry); Update stays disabled while invalid.
- Save failures (exception) → snackbar "Could not update transaction. Please try again."; sheet stays open; nothing persisted.
- `CancellationException` re-thrown (existing convention).

## 7. Testing

TDD-first, matching existing test styles (JUnit 4, MockK, `runTest`).

1. **`TransactionRepositoryImpl` tests** (mock DAO):
   - `updateEditedTransaction` maps domain→entity enums correctly.
   - calls the **targeted** `updateTransactionFields` (never the generic `@Update`) — guards the `smsBodyHash` invariant.
2. **`TransactionsViewModelTest`** (mock repo):
   - row tap initializes the edit form from the transaction's values.
   - valid `updateTransaction()` → calls `updateEditedTransaction` with edited fields; refreshes `selectedTransaction`; snackbar "Transaction updated"; sheet dismissed.
   - invalid amount → no repo call, inline error set.
   - blank description → no repo call, inline error set.
   - demo-data loaded → Update shows the barrier, no repo call.
3. **`validateTransactionEdit` helper test** — blank/zero/negative/unparseable amount; blank/over-length description → exact error strings.

**Expected count:** ~10-12 new tests. Final measured number recorded from the gate run (`./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`).

**Manual smoke (add to TESTING.md):** edit every field on a seeded transaction → list row + detail sheet reflect it; edit an SMS-derived row then re-sync → no duplicate row.

## 8. Files

- Modify `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionDetailSheet.kt` → editable `TransactionEditSheet`
- Modify `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt` — edit form state + `updateTransaction()`
- Modify `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt` — pass edit callbacks to the sheet
- Modify `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt` — add `updateTransactionFields`
- Modify `app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt` + `domain/repository/TransactionRepository.kt` — `updateEditedTransaction`
- Tests: `TransactionsViewModelTest.kt`, `TransactionRepositoryImplTest.kt` (or existing repo test file), new `validateTransactionEdit` test
- Docs: `TESTING.md` (manual smoke row), `TODO.md` (mark item done at completion)
