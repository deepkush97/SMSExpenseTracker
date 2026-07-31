# Manual Transaction Entry Screen — Design Spec

## Overview
Add a Manual Transaction Entry screen reachable from the Transactions FAB. The user fills a form (amount, type, date, bank, payee, reference, category), saves it as a `Transaction` with `parseMethod = MANUAL`, sees a success snackbar, and the cleared form stays ready for the next entry.

## Data Layer Changes (parseMethod + Migration)

### New enum
```kotlin
enum class ParseMethod { SMS, MANUAL }
```
Lives in `core/database/entity/TransactionEntity.kt` alongside `TransactionType`.

### TransactionEntity
Add column with default so the migration backfills existing rows:
```kotlin
val parseMethod: ParseMethod = ParseMethod.SMS
```

### Domain Transaction model
Add field with default so existing constructions compile:
```kotlin
data class Transaction(
    ...
    val createdAt: LocalDateTime,
    val parseMethod: ParseMethod = ParseMethod.SMS
)
```

### Migration
- `SmsExpenseDatabase` version 1 → 2
- `MIGRATION_1_2`:
  ```sql
  ALTER TABLE transactions ADD COLUMN parseMethod TEXT NOT NULL DEFAULT 'SMS'
  ```
- Room `exportSchema = true` already configured — KSP regenerates `app/schemas/2.json` on build

### Mappers
`TransactionRepositoryImpl`:
- `Transaction.toEntity()` → include `parseMethod`
- `TransactionEntity.toDomain()` → include `parseMethod`

### Manual entry defaults
Manual transactions save with:
- `rawSms = ""`
- `smsTimestamp = 0`
- `createdAt = LocalDateTime.now()`
- `transactionDate` = chosen date at `atStartOfDay()`

## Architecture (Approach A: immutable form state)

### ManualEntryViewModel (`ui/screens/manualentry/`)
```kotlin
data class FormErrors(
    val amount: String? = null,
    val payee: String? = null
)

data class ManualEntryUiState(
    val amountInput: String = "",
    val type: TransactionType = TransactionType.DEBIT,
    val transactionDate: LocalDate = LocalDate.now(),
    val bankId: Long? = null,
    val payee: String = "",
    val reference: String = "",
    val categoryId: Long? = null,
    val banks: List<Bank> = emptyList(),
    val categories: List<Category> = emptyList(),
    val errors: FormErrors = FormErrors(),
    val isSaving: Boolean = false,
    val showSavedSnackbar: Boolean = false
)
```

`@HiltViewModel` injecting `BankRepository`, `CategoryRepository`, `TransactionRepository`.

### State updates
- `onAmountChange(value)` — update input, clear `errors.amount`
- `onTypeChange(type)`
- `onDateChange(date)`
- `onBankChange(id)`
- `onPayeeChange(value)` — update, clear `errors.payee`
- `onReferenceChange(value)`
- `onCategoryChange(id)`

### save()
1. Parse amount: "1,250.50" → `125050L` (rupees × 100).
   - `RegexParser.parsePaisa` already implements this but is `private`. **Extract it into a shared pure function** `parsePaisa(input: String): Long?` (e.g. `core/parser/Paisa.kt` or alongside `formatPaisa`), reuse it in both `RegexParser` and `ManualEntryViewModel`. AGENTS.md documents this as the canonical paisa parse.
2. Validate:
   - amount: required, parses, must be `> 0` — else `errors.amount = "Enter a valid amount"`
   - payee: required, max 200 chars — else `errors.payee = "Payee is required"` / `"Payee too long"`
3. If errors → return, don't save.
4. Build description: `reference.isBlank()` → `payee`, else `"$payee · $reference"`.
5. Insert `Transaction(bankId, amountPaisa, type, description, date.atStartOfDay(), categoryId, rawSms="", smsTimestamp=0, createdAt=now, parseMethod=MANUAL)`.
6. On success: clear `amountInput`, `payee`, `reference`, `categoryId`; keep type/date/bank; set `showSavedSnackbar = true`.
7. `consumeSavedSnackbar()` — resets flag after snackbar shown.

Banks default to first bank once loaded (`bankId = banks.first().id` if null).

## Screen (ManualEntryScreen.kt)

```
┌──────────────────────────────┐
│ ←  Add Transaction            │  TopAppBar (back arrow)
├──────────────────────────────┤
│ Amount [₹ 1,250.50      ]    │  OutlinedTextField, Decimal keyboard
│        error: Enter a valid… │
│ (Credit) (Debit)             │  SingleChoiceSegmentedButtonRow
│ Date    [31 Jul 2026  ⌄ ]    │  readOnly field + DatePickerDialog
│ Account [HDFC Bank     ⌄ ]   │  ExposedDropdownMenu (banks)
│ Payee   [Zomato         ]    │  OutlinedTextField + error text
│ Reference [ORD-123456    ]   │  optional, max 100
│ Category [Food         ⌄ ]   │  ExposedDropdownMenu (categories)
│ [           Save           ] │  full-width Button, disabled while isSaving
└──────────────────────────────┘
```

- Amount parse: strip commas, `toDoubleOrNull()` → paisa (consistent with `formatPaisa`)
- Date field: `OutlinedTextField(readOnly=true)` + `DatePickerDialog`
- Bank & Category: `ExposedDropdownMenuBox` (same pattern as `TransactionDetailSheet`)
- Snackbar via `SnackbarHost` on Scaffold, triggered by `showSavedSnackbar`

## Navigation
- Add route `"manual_entry"` to `AppNavHost`
- `TransactionsScreen` FAB `onNavigateToManualEntry` wired to `navController.navigate("manual_entry")` in `NavGraph.kt`
- Not a bottom-nav tab

## Testing

### ManualEntryViewModelTest (`app/src/test/.../manualentry/`)
- invalid amount (blank, "0", "abc") → error set, no insert
- valid amount "1,250.50" → inserts paisa `125050L`
- blank payee → error set, no insert
- payee > 200 chars → error
- valid save → repository.insert called with `parseMethod = MANUAL`, `rawSms = ""`, `description = "Zomato · ORD-123"` (merged) and `"Zomato"` (no reference)
- after save → form cleared (amount/payee/reference/category), `showSavedSnackbar = true`
- `consumeSavedSnackbar()` → flag false

### Existing test updates
- `TransactionRepositoryImplTest`: add `parseMethod` to constructed `Transaction`/assert mapper round-trip
- `TransactionsViewModelTest`: update `mockTransaction` helper with `parseMethod`

### Migration test
- `MIGRATION_1_2` smoke: in-memory Room `MIGRATION_TEST_HELPER` (Room 2.8 `MigrationTestHelper`) verifying v1→v2 adds column and seed data survives

## Files Touched
- `core/database/entity/TransactionEntity.kt` — ParseMethod enum + field
- `core/database/SmsExpenseDatabase.kt` — version 2, MIGRATION_1_2
- `domain/model/Transaction.kt` — parseMethod field
- `data/repository/TransactionRepositoryImpl.kt` — mapper updates
- `ui/screens/manualentry/ManualEntryScreen.kt` — new
- `ui/screens/manualentry/ManualEntryViewModel.kt` — new
- `ui/navigation/NavGraph.kt` — new route + FAB wiring
- `app/schemas/2.json` — regenerated by KSP
- tests: `ManualEntryViewModelTest` (new), `TransactionRepositoryImplTest`, `TransactionsViewModelTest`
