# Transaction List Screen — Design Spec

## Overview
The Transactions tab shows the full transaction ledger with monthly aggregation, search, filtering, a date-sectioned list, and a detail bottom sheet with category editing.

## Screen Layout (top-to-bottom)

```
┌──────────────────────────────────┐
│  Monthly Summary Banner          │
│  ◀  July 2026  ▶                │
│  +₹12,450 Cr    -₹8,320 Dr      │
│  Net: +₹4,130                   │
├──────────────────────────────────┤
│  🔍 Search transactions...      │ ← OutlinedTextField, debounced 300ms
│  [All] [Credit] [Debit] Bank ▾  │ ← FilterChip row + ExposedDropdownMenu
├──────────────────────────────────┤
│  Yesterday                       │ ← date section header
│  ┌─────────────────────────┐    │
│  │ HDFC   🍕 Zomato  Food  │    │ ← TransactionRow + CategoryChip
│  │ Jul 29, 2026    -₹450   │    │
│  └─────────────────────────┘    │
│  ...                             │
│ ─── No more transactions ───    │
├──────────────────────────────────┤
│  [+] FAB                        │ ← navigates to Manual Entry (placeholder)
└──────────────────────────────────┘
```

## Architecture

### ViewModel State

```kotlin
data class TransactionsUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val monthlyCredits: Long = 0,
    val monthlyDebits: Long = 0,
    val netAmount: Long = 0,
    val displayedTransactions: List<Transaction> = emptyList(),
    val allTransactions: List<Transaction> = emptyList(),
    val searchQuery: String = "",
    val filterType: TransactionType? = null,     // null = All
    val selectedBankId: Long? = null,            // null = All Banks
    val banks: List<Bank> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedTransaction: Transaction? = null, // non-null → show bottom sheet
    val isLoading: Boolean = true
)
```

### Data Flow

1. `GetTransactionsUseCase()` emits `Flow<List<Transaction>>` (all transactions)
2. `BankRepository.getAllBanks()` emits `Flow<List<Bank>>`
3. `CategoryRepository.getAllCategories()` emits `Flow<List<Category>>`
4. ViewModel creates a date-filtered subset (by `currentMonth`) for banner aggregation, then further filters for `displayedTransactions`:
   - Filter by `currentMonth` (YearMonth)
   - Filter by `filterType` (CREDIT/DEBIT/All)
   - Filter by `selectedBankId` (or all)
   - Filter by `searchQuery` (case-insensitive contains on description)
5. Monthly credits/debits/net computed from ALL transactions in `currentMonth` (not the filtered subset), so the banner always shows the complete picture for the month regardless of active filters

**Note:** DAO needs an `updateTransactionCategory(id, categoryId)` method for the bottom sheet category picker. This will be added during implementation.
6. UI collects `StateFlow<TransactionsUiState>` with `stateIn(viewModelScope, WhileSubscribed(5000), initial)`

### Month Navigation
- Left/right arrows change `currentMonth` in ViewModel
- Cannot go past current month (no future transactions)
- When month changes, all filters re-apply automatically via Flow combination

## Component Tree

```kotlin
TransactionsScreen(modifier, viewModel, onNavigateToManualEntry)
├── Scaffold
│   ├── FAB → onNavigateToManualEntry()
│   └── Content: LazyColumn
│       ├── MonthlySummaryBanner(currentMonth, credits, debits, net, onPrevMonth, onNextMonth)
│       ├── TransactionSearchBar(query, onQueryChange)
│       ├── TransactionFilterChips(filterType, onFilterChange, banks, selectedBankId, onBankChange)
│       ├── items(transactions, key = { it.id })
│       │   // DateSectionHeader is emitted when transaction.date differs from previous row's date
│       │   // Labels: "Today", "Yesterday", or "28 Jul 2026" for older dates
│       │   └── TransactionListItem(transaction, bankName, categoryName, categoryColor, onClick)
│       └── EmptyState (when no transactions match filters)
│
└── TransactionDetailSheet(transaction, banks, categories, onCategoryChange, onDismiss)
    ├── Full transaction details
    ├── Category picker (ExposedDropdownMenu)
    └── Save button
```

### Key Interactions
- **Tap row** → ViewModel sets `selectedTransaction` → `ModalBottomSheet` appears
- **Change category in sheet** → `TransactionRepository.updateCategory(tx.id, newCategoryId)` → list auto-refreshes
- **Dismiss sheet** → ViewModel clears `selectedTransaction`
- **Debounced search** → 300ms debounce on query Flow before filtering
- **Month change** → re-filter from `allTransactions` (in-memory, no new DB query)

## Edge Cases

| Case | Behavior |
|------|----------|
| No transactions at all | Full-screen `EmptyState` with "Sync SMS" CTA |
| Search yields no results | Inline `EmptyState` (list area only, banner stays visible) |
| Month with no transactions | Banner shows ₹0, list shows empty state |
| Category update fails | Logged to Timber, no user-facing error (non-critical) |
| Bank dropdown with no banks | Hide bank filter, show only type chips |

## Testing

- **TransactionsViewModelTest**: Mock repositories, verify state transitions for search, filter, month change, bottom sheet open/close, category update
- **TransactionsScreenTest** (future): Compose UI tests for row rendering, filter interaction, bottom sheet
- No new DAO/repository tests needed (existing tests cover the queries)

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `ui/screens/transactions/TransactionsViewModel.kt` | **Modify** | Full ViewModel with state, flows, filtering |
| `ui/screens/transactions/TransactionsScreen.kt` | **Modify** | Full screen composable |
| `ui/screens/transactions/TransactionSearchBar.kt` | **Create** | Debounced search field |
| `ui/screens/transactions/TransactionFilterChips.kt` | **Create** | Type chips + bank dropdown |
| `ui/screens/transactions/MonthlySummaryBanner.kt` | **Create** | Month nav + aggregation display |
| `ui/screens/transactions/TransactionListItem.kt` | **Create** | Row with category chip |
| `ui/screens/transactions/TransactionDetailSheet.kt` | **Create** | Bottom sheet with details + category picker |
