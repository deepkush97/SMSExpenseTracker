# Dashboard Screen — Design Spec

> **Goal:** Replace the placeholder Dashboard screen with a financial overview showing summary cards, charts (per-bank bar, monthly line, category donut), and recent transactions.

## Architecture

```
DashboardScreen.kt
  └─ observes: DashboardViewModel.uiState  (StateFlow<DashboardUiState>)
       └─ uses: GetDashboardDataUseCase
            ├─ TransactionRepository.getBankSummary()       → Flow<List<BankSummary>>
            ├─ TransactionRepository.getMonthlySummary()    → Flow<List<MonthlySummary>>
            ├─ TransactionRepository.getCategorySummary()   → Flow<List<CategorySummary>>
            ├─ TransactionRepository.getTotalByType(DEBIT)  → Flow<Long?>
            ├─ TransactionRepository.getTotalByType(CREDIT) → Flow<Long?>
            └─ TransactionRepository.getRecentTransactions() → Flow<List<Transaction>>
```

## Data Layer — New DAO Queries

### DAO Result Classes (in `core/database/`)

```kotlin
data class BankSummary(val bankId: Long, val type: TransactionType, val total: Long)
data class MonthlySummary(val yearMonth: String, val type: TransactionType, val total: Long)
data class CategorySummary(val categoryId: Long?, val total: Long)
data class TotalByType(val type: TransactionType, val total: Long?)
```

### TransactionDao additions

```kotlin
@Query("SELECT bankId, type, SUM(amount) AS total FROM transactions GROUP BY bankId, type")
fun getBankSummary(): Flow<List<BankSummary>>

@Query("SELECT strftime('%Y-%m', transactionDate, 'unixepoch') AS yearMonth, type, SUM(amount) AS total FROM transactions GROUP BY yearMonth, type ORDER BY yearMonth")
fun getMonthlySummary(): Flow<List<MonthlySummary>>

@Query("SELECT categoryId, SUM(amount) AS total FROM transactions WHERE type = 'DEBIT' GROUP BY categoryId")
fun getCategorySummary(): Flow<List<CategorySummary>>

@Query("SELECT SUM(amount) FROM transactions WHERE type = :type")
fun getTotalByType(type: TransactionType): Flow<Long?>

@Query("SELECT * FROM transactions ORDER BY transactionDate DESC LIMIT 5")
fun getRecentTransactions(): Flow<List<TransactionEntity>>
```

### Repository additions

`TransactionRepository` interface gets 5 matching suspend-less `Flow<>` methods. `TransactionRepositoryImpl` delegates + maps entities to domain models.

## Domain Layer — GetDashboardDataUseCase

```kotlin
class GetDashboardDataUseCase(private val repository: TransactionRepository) {
    operator fun invoke(): DashboardData

    data class DashboardData(
        val totalSpent: Flow<Long?>,
        val totalReceived: Flow<Long?>,
        val bankSummary: Flow<List<BankSummary>>,
        val monthlySummary: Flow<List<MonthlySummary>>,
        val categorySummary: Flow<List<CategorySummary>>,
        val recentTransactions: Flow<List<Transaction>>
    )
}
```

Combines all 6 flows into a single data object for the ViewModel. No mapping logic — keeps it a pass-through.

## Presentation Layer

### DashboardUiState

```kotlin
data class DashboardUiState(
    val totalSpent: Long = 0,
    val totalReceived: Long = 0,
    val bankChartData: List<BankBarItem> = emptyList(),
    val monthlyChartData: List<MonthlyLineItem> = emptyList(),
    val categoryChartData: List<CategoryPieItem> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = true
)

data class BankBarItem(val bankName: String, val credit: Long, val debit: Long)
data class MonthlyLineItem(val month: String, val credit: Long, val debit: Long)
data class CategoryPieItem(val categoryName: String, val color: Int, val amount: Long)
```

### DashboardViewModel

```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardDataUseCase: GetDashboardDataUseCase
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = ...
}
```

Uses `combine` on all 6 flows, maps to `DashboardUiState`, starts as loading (emits `isLoading = true` immediately, `false` on first data arrival).

### Summary Cards

3 cards in a horizontal `Row`:

| Card | Content | Color |
|------|---------|-------|
| Total Spent | ₹ amount with down arrow icon | `Red40` |
| Total Received | ₹ amount with up arrow icon | `Green40` |
| Net | ₹ amount (received - spent) | `Blue40` if positive, `Red40` if negative |

Each card: `Card` composable with animated counter (`animateIntAsState`).

### Per-Bank Bar Chart

`ColumnCartesianLayer` (Vico), one clustered group per bank, two columns per group (credit=green, debit=red). Legend at bottom. Empty state if no data.

### Monthly Line Chart

`LineCartesianLayer` (Vico), two lines (credit=green, debit=red), x-axis = month labels. Empty state if no data.

### Category Donut Chart

`PieChartHost` (Vico), each slice = category, colors from `Category.color`. Center hole shows total debit. Empty state if no data.

### Recent Transactions

`Column` (not `LazyColumn` — only 5 items) with `TransactionRow` composable: category colored dot, description, amount (green/red), date. Tappable → bottom sheet (future — placeholder click handler).

### Empty State

When `recentTransactions.isEmpty()` and `!isLoading`: show `EmptyState` composable with "No transactions yet" and "Sync SMS" action button.

### Loading State

Full-screen centered `CircularProgressIndicator` when `isLoading && recentTransactions.isEmpty()`.

## UI Files Created

| File | Package |
|------|---------|
| `SummaryCard.kt` | `ui/screens/dashboard/` |
| `BankChart.kt` | `ui/screens/dashboard/` |
| `MonthlyChart.kt` | `ui/screens/dashboard/` |
| `CategoryChart.kt` | `ui/screens/dashboard/` |
| `TransactionRow.kt` | `ui/screens/dashboard/` |

All chart composables accept a `modifier` and their data model, render Vico chart or empty-state fallback.

## Files Modified

| File | Changes |
|------|---------|
| `TransactionDao.kt` | Add 5 aggregation queries + 3 result data classes |
| `TransactionRepository.kt` | Add 5 new methods |
| `TransactionRepositoryImpl.kt` | Implement 5 new methods |
| `DashboardViewModel.kt` | Full implementation (now empty) |
| `DashboardScreen.kt` | Full implementation (now placeholder) |

## Out of Scope (Phase 2)

- Transaction detail bottom sheet (tapping recent item) — will be built in Task 11
- Pull-to-refresh — future enhancement
- Sync button — future (Task 16 links sync progress)
- Error banner/snackbar — built as-needed (gap in Task 9)

## Verification

1. `./gradlew assembleDebug` compiles
2. `./gradlew testDebugUnitTest` passes
3. App launches → Dashboard shows summary cards with ₹0 values (no data), charts in empty state
4. After adding test transactions → charts render with data

## Implementation Order

1. DAO queries + result data classes
2. Repository interface + impl methods
3. `GetDashboardDataUseCase`
4. `DashboardViewModel` with state
5. Summary cards composable
6. Bank bar chart composable
7. Monthly line chart composable
8. Category donut composable
9. Transaction row composable
10. Assemble `DashboardScreen` with scrollable layout
11. Build + test
