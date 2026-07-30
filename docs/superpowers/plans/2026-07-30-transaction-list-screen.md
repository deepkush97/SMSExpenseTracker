# Transaction List Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a full transaction list screen with monthly summary, search, filter chips, date-sectioned list, detail bottom sheet, and category editing.

**Architecture:** Single ViewModel (`TransactionsViewModel`) with unified `TransactionsUiState` — all filtering (month, type, bank, search query) applied client-side via Flow `.combine` operators. Components are focused composables sharing state through the ViewModel. Category updates go through `TransactionRepository.updateTransactionCategory()`.

**Tech Stack:** Compose, Material3, Hilt ViewModel, Flow, `YearMonth` from `java.time`, `ModalBottomSheet`, `ExposedDropdownMenuBox`, `FilterChip`

## Global Constraints

- All amounts as paisa (`Long`) — display via `formatPaisa()` from `SummaryCard.kt`
- Two `TransactionType` enums: `CREDIT`, `DEBIT`
- Kotlin + Compose + Hilt + Room — no other dependencies
- `kotlinx.coroutines.flow.combine` typed overloads only up to 5 params — use vararg array for more
- Test with JUnit 4 + MockK + `kotlinx-coroutines-test` `runTest { }`

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `core/database/TransactionDao.kt` | Modify (add query) | DAO method to update a transaction's category |
| `domain/repository/TransactionRepository.kt` | Modify (add method) | Interface method `updateTransactionCategory` |
| `data/repository/TransactionRepositoryImpl.kt` | Modify | Implement the new method |
| `ui/screens/transactions/TransactionsViewModel.kt` | Modify | Full ViewModel with state, flows, filtering |
| `ui/screens/transactions/MonthlySummaryBanner.kt` | Create | Month navigation + aggregation display |
| `ui/screens/transactions/TransactionSearchBar.kt` | Create | Debounced search field |
| `ui/screens/transactions/TransactionFilterChips.kt` | Create | Type chips + bank dropdown |
| `ui/screens/transactions/TransactionListItem.kt` | Create | Row with category chip + date section header |
| `ui/screens/transactions/TransactionDetailSheet.kt` | Create | Bottom sheet with details + category picker |
| `ui/screens/transactions/TransactionsScreen.kt` | Modify | Full screen composable assembly |
| `ui/screens/transactions/TransactionsViewModelTest.kt` | Create | ViewModel unit tests |

---

### Task 1: DAO + Repository Category Update

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/TransactionDao.kt` (add `@Query`)
- Modify: `app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt` (add method)
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt` (implement)
- Test: `app/src/test/java/com/smsexpensetracker/data/repository/TransactionRepositoryImplTest.kt`

**Interfaces:**
- Consumes: existing `TransactionDao`, `TransactionRepository` interface, `TransactionRepositoryImpl`
- Produces: `dao.updateTransactionCategory(id: Long, categoryId: Long?)` / `repo.updateTransactionCategory(id: Long, categoryId: Long?)`

- [ ] **Step 1: Add DAO query**

Add to `TransactionDao.kt`:
```kotlin
@Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
suspend fun updateTransactionCategory(id: Long, categoryId: Long?)
```

- [ ] **Step 2: Add repository interface method**

Add to `TransactionRepository.kt`:
```kotlin
suspend fun updateTransactionCategory(id: Long, categoryId: Long?)
```

- [ ] **Step 3: Implement repository method**

Add to `TransactionRepositoryImpl.kt`:
```kotlin
override suspend fun updateTransactionCategory(id: Long, categoryId: Long?) {
    dao.updateTransactionCategory(id, categoryId)
}
```

- [ ] **Step 4: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/database/TransactionDao.kt app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt
git commit -m "feat(dao): add updateTransactionCategory to DAO + repository"
```

---

### Task 2: TransactionsViewModel

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModelTest.kt`

**Interfaces:**
- Consumes: `GetTransactionsUseCase`, `BankRepository`, `CategoryRepository`, `TransactionRepository`
- Produces: `StateFlow<TransactionsUiState>` with all state fields

- [ ] **Step 1: Define UiState**

```kotlin
data class TransactionsUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val monthlyCredits: Long = 0,
    val monthlyDebits: Long = 0,
    val netAmount: Long = 0,
    val displayedTransactions: List<Transaction> = emptyList(),
    val searchQuery: String = "",
    val filterType: TransactionType? = null,     // null = All
    val selectedBankId: Long? = null,            // null = All Banks
    val banks: List<Bank> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedTransaction: Transaction? = null,
    val isLoading: Boolean = true
)
```

- [ ] **Step 2: Write ViewModel class**

```kotlin
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val bankRepository: BankRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow<TransactionType?>(null)
    val filterType: StateFlow<TransactionType?> = _filterType.asStateFlow()

    private val _selectedBankId = MutableStateFlow<Long?>(null)
    val selectedBankId: StateFlow<Long?> = _selectedBankId.asStateFlow()

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _selectedTransaction = MutableStateFlow<Transaction?>(null)
    val selectedTransaction: StateFlow<Transaction?> = _selectedTransaction.asStateFlow()

    val uiState: StateFlow<TransactionsUiState> = combine(
        getTransactionsUseCase(),
        bankRepository.getAllBanks(),
        categoryRepository.getAllCategories(),
        _searchQuery,
        _filterType,
        _selectedBankId,
        _currentMonth
    ) { array ->
        val allTxs = array[0] as List<Transaction>
        val banks = array[1] as List<Bank>
        val categories = array[2] as List<Category>
        val query = array[3] as String
        val type = array[4] as TransactionType?
        val bankId = array[5] as Long?
        val month = array[6] as YearMonth

        val monthTxs = allTxs.filter { tx ->
            YearMonth.from(tx.transactionDate) == month
        }

        val credits = monthTxs.filter { it.transactionType == TransactionType.CREDIT }.sumOf { it.amount }
        val debits = monthTxs.filter { it.transactionType == TransactionType.DEBIT }.sumOf { it.amount }

        val displayed = monthTxs.filter { tx ->
            (type == null || tx.transactionType == type) &&
            (bankId == null || tx.bankId == bankId) &&
            (query.isBlank() || tx.description.contains(query, ignoreCase = true))
        }.sortedByDescending { it.transactionDate }

        TransactionsUiState(
            currentMonth = month,
            monthlyCredits = credits,
            monthlyDebits = debits,
            netAmount = credits - debits,
            displayedTransactions = displayed,
            searchQuery = query,
            filterType = type,
            selectedBankId = bankId,
            banks = banks,
            categories = categories,
            selectedTransaction = _selectedTransaction.value,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsUiState())

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun onFilterTypeChange(type: TransactionType?) { _filterType.value = type }
    fun onBankChange(bankId: Long?) { _selectedBankId.value = bankId }
    fun onMonthChange(month: YearMonth) {
        if (!month.isAfter(YearMonth.now())) _currentMonth.value = month
    }
    fun onTransactionClick(tx: Transaction) { _selectedTransaction.value = tx }
    fun onDismissSheet() { _selectedTransaction.value = null }

    fun onCategoryChange(transactionId: Long, categoryId: Long?) {
        viewModelScope.launch {
            transactionRepository.updateTransactionCategory(transactionId, categoryId)
        }
    }
}
```

Note: Include `import androidx.compose.runtime.getValue` and `by` delegation is NOT needed since we're not using Compose state in the ViewModel — just `StateFlow`.

- [ ] **Step 3: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt
git commit -m "feat(viewmodel): implement TransactionsViewModel with full filtering"
```

---

### Task 3: TransactionListItem Component

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionListItem.kt`

**Interfaces:**
- Consumes: `Transaction`, `Category`, `formatPaisa()`, `Green40`, `Red40`
- Produces: `TransactionListItem(transaction, bankName, categoryName, categoryColor, onClick, modifier)` + `DateSectionHeader(label, modifier)`

- [ ] **Step 1: Create TransactionListItem composable**

```kotlin
@Composable
fun DateSectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun TransactionListItem(
    transaction: Transaction,
    bankName: String,
    categoryName: String?,
    categoryColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bankName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (categoryName != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = (categoryColor ?: Color.Gray).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor ?: Color.Gray,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = transaction.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = transaction.transactionDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatPaisa(transaction.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (transaction.transactionType == TransactionType.CREDIT) Green40 else Red40
        )
    }
}
```

- [ ] **Step 2: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionListItem.kt
git commit -m "feat(ui): add TransactionListItem and DateSectionHeader composables"
```

---

### Task 4: Search Bar + Filter Chips

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionSearchBar.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionFilterChips.kt`

**Interfaces:**
- Consumes: `Bank`, `TransactionType`
- Produces: `TransactionSearchBar(query, onQueryChange, modifier)`, `TransactionFilterChips(filterType, onFilterTypeChange, banks, selectedBankId, onBankChange, modifier)`

- [ ] **Step 1: Create TransactionSearchBar**

```kotlin
@Composable
fun TransactionSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search transactions...") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    )
}
```

Note: Use `Icons.AutoMirrored.Filled.Search` with import `androidx.compose.material.icons.automirrored.filled.Search`.

- [ ] **Step 2: Create TransactionFilterChips**

```kotlin
@Composable
fun TransactionFilterChips(
    filterType: TransactionType?,
    onFilterTypeChange: (TransactionType?) -> Unit,
    banks: List<Bank>,
    selectedBankId: Long?,
    onBankChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var bankExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = filterType == null,
            onClick = { onFilterTypeChange(null) },
            label = { Text("All") }
        )
        FilterChip(
            selected = filterType == TransactionType.CREDIT,
            onClick = { onFilterTypeChange(TransactionType.CREDIT) },
            label = { Text("Credit") }
        )
        FilterChip(
            selected = filterType == TransactionType.DEBIT,
            onClick = { onFilterTypeChange(TransactionType.DEBIT) },
            label = { Text("Debit") }
        )
        Spacer(Modifier.width(8.dp))
        Box {
            ExposedDropdownMenuBox(
                expanded = bankExpanded,
                onExpandedChange = { bankExpanded = it }
            ) {
                OutlinedTextField(
                    value = banks.find { it.id == selectedBankId }?.name ?: "All Banks",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankExpanded) },
                    modifier = Modifier.menuAnchor().weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                ExposedDropdownMenu(
                    expanded = bankExpanded,
                    onDismissRequest = { bankExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Banks") },
                        onClick = { onBankChange(null); bankExpanded = false }
                    )
                    banks.forEach { bank ->
                        DropdownMenuItem(
                            text = { Text(bank.name) },
                            onClick = { onBankChange(bank.id); bankExpanded = false }
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionSearchBar.kt app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionFilterChips.kt
git commit -m "feat(ui): add TransactionSearchBar and TransactionFilterChips composables"
```

---

### Task 5: MonthlySummaryBanner

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/MonthlySummaryBanner.kt`

**Interfaces:**
- Consumes: `formatPaisa()`, `Green40`, `Red40`, `YearMonth`
- Produces: `MonthlySummaryBanner(yearMonth, credits, debits, net, onPrevMonth, onNextMonth, modifier)`

- [ ] **Step 1: Create MonthlySummaryBanner composable**

```kotlin
@Composable
fun MonthlySummaryBanner(
    yearMonth: YearMonth,
    credits: Long,
    debits: Long,
    net: Long,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canGoNext = yearMonth < YearMonth.now()

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onNextMonth, enabled = canGoNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Credits", style = MaterialTheme.typography.labelSmall)
                    Text(formatPaisa(credits), style = MaterialTheme.typography.bodyLarge, color = Green40)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Debits", style = MaterialTheme.typography.labelSmall)
                    Text(formatPaisa(debits), style = MaterialTheme.typography.bodyLarge, color = Red40)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Net", style = MaterialTheme.typography.labelSmall)
                    Text(
                        formatPaisa(net),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (net >= 0) Green40 else Red40,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
```

Note: Use `Icons.AutoMirrored.Filled.KeyboardArrowLeft` and `KeyboardArrowRight`. Import from `androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft`.

- [ ] **Step 2: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/transactions/MonthlySummaryBanner.kt
git commit -m "feat(ui): add MonthlySummaryBanner with month navigation and aggregation"
```

---

### Task 6: TransactionDetailSheet

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionDetailSheet.kt`

**Interfaces:**
- Consumes: `Transaction`, `Bank`, `Category`, `formatPaisa()`
- Produces: `TransactionDetailSheet(transaction, banks, categories, onCategoryChange, onDismiss)`

- [ ] **Step 1: Create TransactionDetailSheet composable**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction: Transaction,
    banks: List<Bank>,
    categories: List<Category>,
    onCategoryChange: (transactionId: Long, categoryId: Long?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var categoryExpanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == transaction.categoryId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Transaction Details", style = MaterialTheme.typography.titleLarge)

            DetailRow("Amount", formatPaisa(transaction.amount))
            DetailRow("Type", transaction.transactionType.name)
            DetailRow("Bank", banks.find { it.id == transaction.bankId }?.name ?: "Unknown")
            DetailRow("Date", transaction.transactionDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")))
            DetailRow("Description", transaction.description)

            Divider()

            Text("Category", style = MaterialTheme.typography.titleSmall)
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "Uncategorized",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            onCategoryChange(transaction.id, null)
                            categoryExpanded = false
                        }
                    )
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                onCategoryChange(transaction.id, cat.id)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
```

- [ ] **Step 2: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionDetailSheet.kt
git commit -m "feat(ui): add TransactionDetailSheet with category picker"
```

---

### Task 7: TransactionsScreen Assembly

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt`

**Interfaces:**
- Consumes: All composables from Tasks 3-6, `TransactionsViewModel`, `EmptyState`, `Icons.AutoMirrored.Filled.Add`
- Produces: `TransactionsScreen(modifier, viewModel, onNavigateToManualEntry)`

- [ ] **Step 1: Write TransactionsScreen**

```kotlin
@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    onNavigateToManualEntry: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToManualEntry) {
                Icon(Icons.AutoMirrored.Filled.Add, contentDescription = "Add transaction")
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.displayedTransactions.isEmpty() && state.searchQuery.isBlank() && state.filterType == null && state.selectedBankId == null -> {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "No transactions yet",
                    subtitle = "Sync your SMS to get started, or tap + to add manually",
                    actionLabel = "Sync SMS",
                    onAction = { /* TODO: trigger sync when SyncUseCase is ready */ }
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        bottom = 88.dp
                    )
                ) {
                    item { MonthlySummaryBanner(...) }
                    item { Spacer(Modifier.height(12.dp)) }
                    item { TransactionSearchBar(...) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { TransactionFilterChips(...) }
                    item { Spacer(Modifier.height(4.dp)) }

                    if (state.displayedTransactions.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.AutoMirrored.Filled.Search,
                                title = "No results",
                                subtitle = "Try a different search or filter",
                                modifier = Modifier.height(300.dp)
                            )
                        }
                    } else {
                        val grouped = state.displayedTransactions.groupBy { tx ->
                            val date = tx.transactionDate.toLocalDate()
                            val today = LocalDate.now()
                            when {
                                date == today -> "Today"
                                date == today.minusDays(1) -> "Yesterday"
                                else -> date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                            }
                        }
                        grouped.forEach { (header, txs) ->
                            item(key = header) { DateSectionHeader(header) }
                            items(txs, key = { it.id }) { tx ->
                                TransactionListItem(
                                    transaction = tx,
                                    bankName = state.banks.find { it.id == tx.bankId }?.name ?: "Unknown",
                                    categoryName = tx.categoryId?.let { cid -> state.categories.find { it.id == cid }?.name },
                                    categoryColor = tx.categoryId?.let { cid -> state.categories.find { it.id == cid } }?.let { Color(it.color) },
                                    onClick = { viewModel.onTransactionClick(tx) }
                                )
                                if (tx != grouped.values.last().last()) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.selectedTransaction?.let { tx ->
        TransactionDetailSheet(
            transaction = tx,
            banks = state.banks,
            categories = state.categories,
            onCategoryChange = viewModel::onCategoryChange,
            onDismiss = viewModel::onDismissSheet
        )
    }
}
```

Key details:
- Uses `hiltViewModel()` from `androidx.hilt.lifecycle.viewmodel.compose`
- Month navigation arrows call `viewModel.onMonthChange(month.minusMonths(1))` / `viewModel.onMonthChange(month.plusMonths(1))`
- Empty states handled: no transactions at all vs no search results
- HorizontalDivider between items (not after the last one)
- FAB at bottom-right, navigates to manual entry

- [ ] **Step 2: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt
git commit -m "feat(ui): implement full TransactionsScreen with FAB, empty states, and bottom sheet"
```

---

### Task 8: ViewModel Tests

**Files:**
- Create: `app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModelTest.kt`

- [ ] **Step 1: Write ViewModel test**

```kotlin
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getTransactionsUseCase: GetTransactionsUseCase
    private lateinit var bankRepository: BankRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var viewModel: TransactionsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getTransactionsUseCase = mockk()
        bankRepository = mockk()
        categoryRepository = mockk()
        transactionRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockTransaction(
        id: Long = 1L,
        bankId: Long = 1L,
        amount: Long = 10000L,
        type: TransactionType = TransactionType.DEBIT,
        description: String = "Test",
        transactionDate: LocalDateTime = LocalDateTime.now(),
        categoryId: Long? = null
    ): Transaction = Transaction(
        id = id, bankId = bankId, amount = amount, transactionType = type,
        description = description, transactionDate = transactionDate,
        categoryId = categoryId, rawSms = "", smsTimestamp = 0L, createdAt = LocalDateTime.now()
    )

    @Test
    fun `transactions are filtered by selected month`() = runTest(testDispatcher) {
        val janTx = mockTransaction(transactionDate = LocalDateTime.of(2026, 1, 15, 10, 0))
        val febTx = mockTransaction(transactionDate = LocalDateTime.of(2026, 2, 15, 10, 0))
        val txFlow = MutableStateFlow(listOf(janTx, febTx))

        coEvery { getTransactionsUseCase() } returns txFlow
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository)
        advanceUntilIdle()
        viewModel.onMonthChange(YearMonth.of(2026, 1))
        advanceUntilIdle()

        assert(viewModel.uiState.value.displayedTransactions.size == 1)
        assert(viewModel.uiState.value.displayedTransactions[0].id == janTx.id)
    }

    @Test
    fun `search query filters by description`() = runTest(testDispatcher) {
        val tx1 = mockTransaction(description = "Zomato order", transactionDate = LocalDateTime.now())
        val tx2 = mockTransaction(description = "Swiggy order", transactionDate = LocalDateTime.now())
        val txFlow = MutableStateFlow(listOf(tx1, tx2))

        coEvery { getTransactionsUseCase() } returns txFlow
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository)
        advanceUntilIdle()
        viewModel.onSearchQueryChange("Zomato")
        advanceUntilIdle()

        assert(viewModel.uiState.value.displayedTransactions.size == 1)
        assert(viewModel.uiState.value.displayedTransactions[0].id == tx1.id)
    }

    @Test
    fun `filter type limits displayed transactions`() = runTest(testDispatcher) {
        val credit = mockTransaction(type = TransactionType.CREDIT, transactionDate = LocalDateTime.now())
        val debit = mockTransaction(type = TransactionType.DEBIT, transactionDate = LocalDateTime.now())
        val txFlow = MutableStateFlow(listOf(credit, debit))

        coEvery { getTransactionsUseCase() } returns txFlow
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository)
        advanceUntilIdle()
        viewModel.onFilterTypeChange(TransactionType.CREDIT)
        advanceUntilIdle()

        assert(viewModel.uiState.value.displayedTransactions.size == 1)
        assert(viewModel.uiState.value.displayedTransactions[0].transactionType == TransactionType.CREDIT)
    }

    @Test
    fun `month navigation cannot go to future months`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository)
        val futureMonth = YearMonth.now().plusMonths(1)
        viewModel.onMonthChange(futureMonth)
        advanceUntilIdle()

        assert(viewModel.uiState.value.currentMonth != futureMonth)
    }

    @Test
    fun `onCategoryChange calls repository`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        coEvery { transactionRepository.updateTransactionCategory(any(), any()) } returns Unit

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository)
        viewModel.onCategoryChange(1L, 5L)
        advanceUntilIdle()

        coVerify { transactionRepository.updateTransactionCategory(1L, 5L) }
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModelTest.kt
git commit -m "test: add TransactionsViewModel unit tests"
```

---

### Task 9: Final Build Verification

- [ ] **Step 1: Clean build + tests**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Update TODO.md**

Mark Task 11 items as `[x]` where completed.

- [ ] **Step 3: Final commit**

```bash
git add TODO.md
git commit -m "docs: mark Task 11 as complete in TODO"
```
