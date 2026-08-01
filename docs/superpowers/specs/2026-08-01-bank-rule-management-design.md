# Bank & SMS Rule Management — Design Spec

**Date:** 2026-08-01
**Status:** Approved
**Task reference:** TODO.md Task 14 (Settings Screen), second sub-feature

## 1. Overview

Users can manage banks and their SMS parsing rules from the Settings screen. This is the second slice of Task 14 (after category management). It lets users add/edit/delete banks, and per-bank add/edit/delete/enable-disable the regex rules that `ParserEngine` uses to parse SMS. User-added rules are picked up live by the parser (no rebuild).

## 2. Goals

- CRUD for banks; delete guarded so a bank with transactions cannot be deleted (FK CASCADE would wipe transactions + rules).
- Per-bank CRUD for SMS rules, plus an enable/disable toggle.
- Rule pattern validated as a compilable Kotlin `Regex` before saving.
- Disabled rules excluded from `SmsSyncUseCase` and `ParserViewModel` (Parser Test screen).
- Reachable via a "Banks & Rules" row in SettingsScreen, drill-down: banks list → bank detail (rules).

## 3. Non-Goals

- Regex pattern preview / test-in-dialog.
- Moving a rule to a different bank (create a new rule instead).
- Bank reordering, bank icon/color.
- Rule/bank import-export.
- The rest of Task 14 (sync controls, CSV, log viewer, about-section polish).

## 4. Schema Change (v4 → v5)

`SmsRuleEntity` gains a new field:

```kotlin
val isActive: Boolean = true
```

- `@Database(version = 5)`.
- New `MIGRATION_4_5`:
  `db.execSQL("ALTER TABLE sms_rules ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")`
- Room schema export regenerates `app/schemas/com.smsexpensetracker.core.database.SmsExpenseDatabase/5.json`.
- `SeedDatabaseCallback.seedSmsRules` inserts unchanged (Room default `isActive=1` applies at insert time since the column has a SQL default; the entity field default keeps seed/parse code working).

**Delete semantics (existing, unchanged):** `banks` ← FK `CASCADE` on `transactions.bankId` and `sms_rules.bankId`. Deleting a bank wipes its transactions and rules — hence the delete guard (§6).

## 5. Repository Layer

### BankRepository (add write methods)
```kotlin
suspend fun insert(bank: Bank): Long
suspend fun update(bank: Bank)
suspend fun delete(bank: Bank)
suspend fun countTransactions(bankId: Long): Int
```
- `countTransactions` → new `TransactionDao` query `SELECT COUNT(*) FROM transactions WHERE bankId = :bankId`.
- `BankRepositoryImpl`: delegate to existing `BankDao.insert/update/delete` + new count query; add `BankEntity.toDomain` / `Bank.toEntity` mappers.

### SmsRuleRepository (add methods)
```kotlin
suspend fun update(rule: SmsRule)
suspend fun delete(rule: SmsRule)
```
- Existing `insert(rule: SmsRule): Long` already exists — update its mapper to carry `isActive`.
- `SmsRuleRepositoryImpl`: delegate to existing `SmsRuleDao.update/delete`; update `toEntity()` to include `isActive`.

## 6. Parser Integration (active-only filtering)

Two call sites filter rules to active ones. `ParserEngine.parse(smsBody, sender, rules: List<Pair<Long, String>>)` is **unchanged** — it still receives only patterns.

- `SmsSyncUseCase.sync()`: change
  `val rules = smsRuleRepository.getAllRules().first()`
  to filter `.filter { it.isActive }` before mapping.
- `ParserViewModel.resolveRules()`: change `allRules.filter { it.bankId == bankId }` to also filter `.filter { it.isActive }`.
- `ParserViewModel` already loads `allRules` once in `init` — a toggled rule in Settings will be reflected next time the Parser screen's ViewModel is created. Acceptable for this feature (no live cross-screen sync required).

## 7. ViewModels

### BankManagementViewModel (`ui/screens/banks/`)
- `@HiltViewModel`, injects `BankRepository`.
- `val banks: StateFlow<List<Bank>>` — `getAllBanks()` → `stateIn(viewModelScope, WhileSubscribed(5000), emptyList())`.
- `fun addBank(name: String, smsSender: String)` → `insert(Bank(0, name.trim(), smsSender.trim().uppercase()))`.
- `fun updateBank(bank: Bank)` → `update(bank)`.
- `fun deleteBank(bank: Bank)` → guard: `if (repository.countTransactions(bank.id) > 0) return`; then `delete(bank)`.
- `suspend fun transactionCount(bankId: Long): Int` exposed for the UI to decide delete-disabled state (or the UI derives it from a combined state; implementation detail left to the plan — the guard MUST be in the ViewModel regardless).

### BankDetailViewModel (`ui/screens/banks/`)
- `@HiltViewModel`, injects `BankRepository` + `SmsRuleRepository`. Constructor takes `bankId: Long` via `@HiltViewModel` + `SavedStateHandle`.
- `val bank: StateFlow<Bank?>` — `getBankById(bankId)`.
- `val rules: StateFlow<List<SmsRule>>` — `getRulesForBank(bankId)` → `stateIn`.
- `fun addRule(description: String, pattern: String, isActive: Boolean = true)` → `insert`.
- `fun updateRule(rule: SmsRule)` → `update`.
- `fun deleteRule(rule: SmsRule)` → `delete`.
- `fun setRuleActive(rule: SmsRule, active: Boolean)` → `update(rule.copy(isActive = active))`.

## 8. Screens (`ui/screens/banks/`)

### `BankManagementScreen.kt`
- Scaffold, TopAppBar "Banks" + back, FAB `+` (add).
- `LazyColumn` of bank rows: name + `smsSender` subtitle; tap → `onBankClick(bank)` (navigate to detail); trailing edit `IconButton` → edit dialog; trailing delete `IconButton` (disabled when bank has transactions) → confirm dialog.
- Delete-disabled presentation: `IconButton(enabled = bankCanBeDeleted)`, plus a snackbar/note "Has N transactions" when tapped while disabled (exact mechanism in plan).

### `BankDetailScreen.kt`
- TopAppBar = bank name + back. Header text = bank name, subtitle = smsSender.
- `LazyColumn` of rules: description, pattern in monospace (truncated), a `Switch` bound to `isActive`; tap row → edit dialog; delete icon → confirm dialog.
- Empty state when the bank has no rules: "No rules for this bank — add one".
- FAB `+` → add rule dialog.

### `BankDialog.kt`
- Name `OutlinedTextField` (required, ≤30 chars, case-insensitively unique vs other banks).
- Sender `OutlinedTextField` (required, auto-uppercased, non-blank after trim).
- Save enabled only when both valid.

### `RuleDialog.kt`
- Description `OutlinedTextField` (required, ≤60 chars).
- Pattern `OutlinedTextField` — multiline, monospace — validated via `validatePattern(pattern)`.
- Save enabled only when valid.

### `BankDeleteDialog.kt` / `RuleDeleteDialog.kt`
- Bank: "Delete {name}?" + "This bank and its SMS rules will be removed." (Only reachable when transaction count is 0.)
- Rule: "Delete {description}?" + "This SMS rule will no longer be used to parse transactions."

## 9. Validation Helpers (`ui/util/BankRulesValidation.kt`)

```kotlin
fun validateBankName(name: String, existing: List<Bank>, editingId: Long?): String?
fun validateBankSender(sender: String): String?
fun validateRuleDescription(description: String): String?
fun validatePattern(pattern: String): String?
```

Rules:
- `validateBankName`: blank → "Name is required"; >30 → "Name must be 30 characters or fewer"; case-insensitive duplicate (excluding self) → "A bank with this name already exists"; else null.
- `validateBankSender`: blank after trim → "Sender is required"; else null (no format restriction beyond non-blank; TRAI DLT ids vary).
- `validateRuleDescription`: blank → "Description is required"; >60 → "Description must be 60 characters or fewer"; else null.
- `validatePattern`: blank → "Pattern is required"; else try `Regex(pattern)` — on `PatternSyntaxException` → "Pattern must be a valid regular expression"; else null.

## 10. Navigation

- `NavGraph.kt`:
  - `composable("banks")` → `BankManagementScreen(onBack, onBankClick = { navController.navigate("banks/${it.id}") })`
  - `composable("banks/{bankId}")` with `navArgument("bankId") { type = NavType.LongType }` → `BankDetailScreen(onBack = popBackStack)`.
- `SettingsScreen`: add a "Banks & Rules" row (same visual pattern as the Categories row) below or above Categories in the "Data" section; `onNavigateToBanks` param, wired to `navController.navigate("banks")`.

## 11. Testing

- `BankManagementViewModelTest` (MockK + runTest): banks flow emission; addBank (trim + uppercase sender, `Bank(0,...)`); updateBank; deleteBank when 0 transactions; deleteBank guarded when >0 (verify delete never called).
- `BankDetailViewModelTest`: rules flow emission; addRule; updateRule; deleteRule; setRuleActive copies with flipped `isActive`; bank flow emission.
- `BankRulesValidationTest`: all four validators — blank/length/duplicate/regex-invalid cases, valid cases return null.
- `MigrationTest` (device): `migrate4To5_addsIsActiveColumn` — create v4 with a sms_rules row, migrate, assert column exists and default is 1.
- Repo tests: none required (thin delegation, same as Categories).

## 12. Files

**New:**
- `ui/util/BankRulesValidation.kt`
- `ui/screens/banks/BankManagementViewModel.kt`
- `ui/screens/banks/BankDetailViewModel.kt`
- `ui/screens/banks/BankManagementScreen.kt`
- `ui/screens/banks/BankDetailScreen.kt`
- `ui/screens/banks/BankDialog.kt`
- `ui/screens/banks/RuleDialog.kt`
- `ui/screens/banks/BankDeleteDialog.kt`
- `ui/screens/banks/RuleDeleteDialog.kt`
- `app/src/test/.../ui/util/BankRulesValidationTest.kt`
- `app/src/test/.../ui/screens/banks/BankManagementViewModelTest.kt`
- `app/src/test/.../ui/screens/banks/BankDetailViewModelTest.kt`

**Modified:**
- `core/database/entity/SmsRuleEntity.kt` (+`isActive`)
- `core/database/SmsExpenseDatabase.kt` (version 5, `MIGRATION_4_5`)
- `core/database/dao/TransactionDao.kt` (+count-by-bank)
- `domain/repository/BankRepository.kt` (+4 methods)
- `domain/repository/SmsRuleRepository.kt` (+update, +delete)
- `data/repository/BankRepositoryImpl.kt`, `data/repository/SmsRuleRepositoryImpl.kt`
- `domain/usecase/SmsSyncUseCase.kt` (active filter)
- `ui/screens/parser/ParserViewModel.kt` (active filter)
- `ui/navigation/NavGraph.kt`
- `ui/screens/settings/SettingsScreen.kt`
- `app/src/androidTest/.../core/database/MigrationTest.kt`
- `app/schemas/.../5.json` (generated)
- `TODO.md`

## 13. Verification

- `./gradlew testDebugUnitTest assembleDebug` — all tests pass, build succeeds.
- `app/schemas/.../5.json` committed.
- Manual smoke: add bank, add rule, disable a rule and confirm Parser Test ignores it, delete bank with 0 transactions succeeds, delete blocked for bank with transactions.
