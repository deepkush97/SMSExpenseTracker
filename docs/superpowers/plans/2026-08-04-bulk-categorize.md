# Bulk-Categorize Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated "Categorize" bottom-nav tab that walks through transactions one card at a time, letting the user assign (or reassign) a category via a dropdown before moving on — so SMS-parsed "Uncategorized" transactions get categorized quickly.

**Architecture:** A new `CategorizeViewModel` combines `TransactionRepository.getAllTransactions()` + `CategoryRepository.getAllCategories()` + `BankRepository.getAllBanks()` into a snapshot queue (uncategorized first, then by date desc). Each card shows the current transaction's details + a category dropdown; selecting a category calls the existing `updateTransactionCategory(id, categoryId)` and advances; `skip()` advances without writing. A new `BottomNavItem.Categorize` (route `categorize`) renders the screen; the bottom nav bar already renders from `BottomNavItem.items`, so this is additive.

**Tech Stack:** Kotlin, Compose M3, Navigation Compose, Hilt, Room Flow, MockK + kotlinx-coroutines-test (JUnit 4).

## Global Constraints

- Package root: `com.smsexpensetracker`. Money is paisa `Long` (`formatPaisa` from `ui/util`). No code comments unless a task's code block includes them.
- Build gate (no lint/typecheck): `./gradlew assembleDebug` and `./gradlew cleanTestDebugUnitTest testDebugUnitTest`.
- Test baseline: 332 tests green. The demo-data-gate plan (separate plan file) does NOT need to land first — these features are independent; but if both are executed in the same working tree, run this plan's gate after both land.
- Reuses `TransactionRepository.updateTransactionCategory(id, categoryId)` — the same path the Transactions detail sheet uses. No changes to the repository.
- Queue ordering (from spec §2.2): **uncategorized first** (`categoryId == null`), then the rest; each group sorted by `transactionDate` descending. The queue is a snapshot — no re-sort mid-flow.
- User-approved UX: card-by-card with a **dropdown** (ExposedDropdownMenu, same pattern as `TransactionDetailSheet`); selecting a category applies immediately (`assignCategory`) and advances; **Skip** advances without writing.
- New tab is single-purpose: only the categorize flow. No demo gating on this screen.
- Commit directly to `main`. NEVER stage the pre-existing dirty `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardViewModel.kt`, `opencode.json`, or untracked plan/spec docs.

---

### Task 1: Add `BottomNavItem.Categorize` + route

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/BottomNavItem.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt`

**Interfaces:**
- Produces: `BottomNavItem.Categorize` (route `"categorize"`, label `"Categorize"`, `Icons.Filled.Sell`) in the `items` list; `composable("categorize")` route calling `CategorizeScreen()` (created in Task 3). `MainActivity` needs no change — it renders from `BottomNavItem.items`.

- [ ] **Step 1: Add the nav item**

In `BottomNavItem.kt`, add `import androidx.compose.material.icons.filled.Sell`, add the new item after `Transactions`:
```kotlin
    data object Categorize : BottomNavItem("categorize", "Categorize", Icons.Filled.Sell)
```
and add it to the companion `items` list:
```kotlin
        val items = listOf(Dashboard, Transactions, Categorize, Parser, Settings)
```

- [ ] **Step 2: Add the route**

In `NavGraph.kt`, add `import com.smsexpensetracker.ui.screens.categorize.CategorizeScreen`, and after the `Transactions` composable block:
```kotlin
        composable(BottomNavItem.Categorize.route) {
            CategorizeScreen()
        }
```

- [ ] **Step 3: Create the stub screen + build**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeScreen.kt` as a minimal composable that compiles WITHOUT `CategorizeViewModel` (which is created in Task 2). This keeps every task's commit buildable:

```kotlin
package com.smsexpensetracker.ui.screens.categorize

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CategorizeScreen(
    modifier: Modifier = Modifier
) {
}
```

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/navigation/BottomNavItem.kt app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeScreen.kt
git commit -m "feat(nav): add categorize tab"
```

---

### Task 2: `CategorizeViewModel`

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/categorize/CategorizeViewModelTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository` (existing), `CategoryRepository` (existing), `BankRepository` (existing). `TransactionRepository.updateTransactionCategory(id: Long, categoryId: Long?)` is a `suspend fun` returning `Unit`.
- Produces: `CategorizeUiState(queue: List<Transaction>, index: Int, categories: List<Category>, banks: List<Bank>, assignedCount: Int)` with computed `val current: Transaction?` and `val isDone: Boolean`; `uiState: StateFlow<CategorizeUiState>`; `fun assignCategory(categoryId: Long?)`, `fun skip()`, `fun reset()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smsexpensetracker.ui.screens.categorize

import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class CategorizeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val bankRepository = mockk<BankRepository>()

    private val food = Category(id = 3, name = "Food", icon = "", color = 0xFF0000, isDefault = false)
    private val travel = Category(id = 4, name = "Travel", icon = "", color = 0xFF00FF00, isDefault = false)
    private val hdfc = Bank(id = 1, name = "HDFC Bank", smsSender = "HDFCBK")

    private fun tx(
        id: Long,
        date: LocalDate = LocalDate.of(2026, 8, 1),
        categoryId: Long? = null
    ) = Transaction(
        id = id,
        bankId = 1L,
        amount = 100L,
        transactionType = TransactionType.DEBIT,
        description = "Tx $id",
        transactionDate = date.atStartOfDay(),
        categoryId = categoryId,
        rawSms = "",
        smsTimestamp = 0L,
        createdAt = LocalDateTime.now(),
        parseMethod = ParseMethod.SMS
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `queue orders uncategorized first then date desc within groups`() = runTest(testDispatcher) {
        val uncategorizedOld = tx(1, LocalDate.of(2026, 7, 1))
        val uncategorizedNew = tx(2, LocalDate.of(2026, 8, 5))
        val categorizedOld = tx(3, LocalDate.of(2026, 7, 10), categoryId = 3L)
        val categorizedNew = tx(4, LocalDate.of(2026, 8, 10), categoryId = 4L)
        every { transactionRepository.getAllTransactions() } returns flowOf(
            listOf(categorizedOld, uncategorizedNew, uncategorizedOld, categorizedNew)
        )
        every { categoryRepository.getAllCategories() } returns flowOf(listOf(food, travel))
        every { bankRepository.getAllBanks() } returns flowOf(listOf(hdfc))

        val vm = CategorizeViewModel(transactionRepository, categoryRepository, bankRepository)
        advanceUntilIdle()

        val ids = vm.uiState.value.queue.map { it.id }
        assertEquals(listOf(2L, 1L, 4L, 3L), ids)
        assertEquals(tx(2).id, vm.uiState.value.current?.id)
        assertFalse(vm.uiState.value.isDone)
    }

    @Test
    fun `assignCategory writes category and advances`() = runTest(testDispatcher) {
        every { transactionRepository.getAllTransactions() } returns flowOf(listOf(tx(1), tx(2)))
        every { categoryRepository.getAllCategories() } returns flowOf(listOf(food, travel))
        every { bankRepository.getAllBanks() } returns flowOf(listOf(hdfc))
        coEvery { transactionRepository.updateTransactionCategory(1L, 3L) } returns Unit

        val vm = CategorizeViewModel(transactionRepository, categoryRepository, bankRepository)
        advanceUntilIdle()

        vm.assignCategory(3L)
        advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.updateTransactionCategory(1L, 3L) }
        assertEquals(1, vm.uiState.value.index)
        assertEquals(1, vm.uiState.value.assignedCount)
        assertEquals(tx(2).id, vm.uiState.value.current?.id)
    }

    @Test
    fun `assignCategory null writes none`() = runTest(testDispatcher) {
        every { transactionRepository.getAllTransactions() } returns flowOf(listOf(tx(1)))
        every { categoryRepository.getAllCategories() } returns flowOf(listOf(food))
        every { bankRepository.getAllBanks() } returns flowOf(listOf(hdfc))
        coEvery { transactionRepository.updateTransactionCategory(1L, null) } returns Unit

        val vm = CategorizeViewModel(transactionRepository, categoryRepository, bankRepository)
        advanceUntilIdle()

        vm.assignCategory(null)
        advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.updateTransactionCategory(1L, null) }
        assertTrue(vm.uiState.value.isDone)
    }

    @Test
    fun `skip advances without writing`() = runTest(testDispatcher) {
        every { transactionRepository.getAllTransactions() } returns flowOf(listOf(tx(1), tx(2)))
        every { categoryRepository.getAllCategories() } returns flowOf(listOf(food))
        every { bankRepository.getAllBanks() } returns flowOf(listOf(hdfc))

        val vm = CategorizeViewModel(transactionRepository, categoryRepository, bankRepository)
        advanceUntilIdle()

        vm.skip()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.index)
        assertEquals(0, vm.uiState.value.assignedCount)
        coVerify(exactly = 0) { transactionRepository.updateTransactionCategory(any(), any()) }
    }

    @Test
    fun `empty queue has no current transaction and is done`() = runTest(testDispatcher) {
        every { transactionRepository.getAllTransactions() } returns flowOf(emptyList())
        every { categoryRepository.getAllCategories() } returns flowOf(listOf(food))
        every { bankRepository.getAllBanks() } returns flowOf(listOf(hdfc))

        val vm = CategorizeViewModel(transactionRepository, categoryRepository, bankRepository)
        advanceUntilIdle()

        assertNull(vm.uiState.value.current)
        assertTrue(vm.uiState.value.queue.isEmpty())
    }

    @Test
    fun `reset returns to first card`() = runTest(testDispatcher) {
        every { transactionRepository.getAllTransactions() } returns flowOf(listOf(tx(1), tx(2)))
        every { categoryRepository.getAllCategories() } returns flowOf(listOf(food))
        every { bankRepository.getAllBanks() } returns flowOf(listOf(hdfc))

        val vm = CategorizeViewModel(transactionRepository, categoryRepository, bankRepository)
        advanceUntilIdle()
        vm.skip()
        vm.skip()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isDone)

        vm.reset()

        assertEquals(0, vm.uiState.value.index)
        assertEquals(0, vm.uiState.value.assignedCount)
    }
}
```

Note: `sortedWith(compareBy<Transaction> { it.categoryId != null }.thenByDescending { it.transactionDate })` is stable, so equal dates keep input order — the input above is already deduped so the expected order is unambiguous.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.categorize.CategorizeViewModelTest" -v`
Expected: COMPILATION ERROR — `CategorizeViewModel` does not exist.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.smsexpensetracker.ui.screens.categorize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategorizeUiState(
    val queue: List<Transaction> = emptyList(),
    val index: Int = 0,
    val categories: List<Category> = emptyList(),
    val banks: List<Bank> = emptyList(),
    val assignedCount: Int = 0
) {
    val current: Transaction? get() = queue.getOrNull(index)
    val isDone: Boolean get() = queue.isNotEmpty() && index >= queue.size
}

@HiltViewModel
class CategorizeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val bankRepository: BankRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategorizeUiState())
    val uiState: StateFlow<CategorizeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val transactions = transactionRepository.getAllTransactions().first()
            val categories = categoryRepository.getAllCategories().first()
            val banks = bankRepository.getAllBanks().first()
            val queue = transactions.sortedWith(
                compareBy<Transaction> { it.categoryId != null }
                    .thenByDescending { it.transactionDate }
            )
            _uiState.update { it.copy(queue = queue, categories = categories, banks = banks) }
        }
    }

    fun assignCategory(categoryId: Long?) {
        val state = _uiState.value
        val current = state.current ?: return
        viewModelScope.launch {
            transactionRepository.updateTransactionCategory(current.id, categoryId)
            _uiState.update {
                it.copy(index = it.index + 1, assignedCount = it.assignedCount + 1)
            }
        }
    }

    fun skip() {
        _uiState.update { it.copy(index = it.index + 1) }
    }

    fun reset() {
        _uiState.update { it.copy(index = 0, assignedCount = 0) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.categorize.CategorizeViewModelTest" -v`
Expected: PASS (6 tests). Then verify the whole app still compiles:

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/categorize/CategorizeViewModelTest.kt
git commit -m "feat(categorize): add view model with snapshot queue and assign/skip"
```

---

### Task 3: `CategorizeScreen`

**Files:**
- Create (replace the Task 1 stub): `app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeScreen.kt`

**Interfaces:**
- Consumes: `CategorizeViewModel` (Task 2) — `uiState`, `assignCategory(Long?)`, `skip()`, `reset()`. `formatPaisa` from `com.smsexpensetracker.ui.util`. `EmptyState` from `ui/components`.
- Produces: the final `CategorizeScreen` composable rendering empty / done / card states.

- [ ] **Step 1: Write the screen**

Replace the Task 1 stub body with the full screen:

```kotlin
package com.smsexpensetracker.ui.screens.categorize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.ui.components.EmptyState
import com.smsexpensetracker.ui.util.formatPaisa
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorizeScreen(
    modifier: Modifier = Modifier,
    viewModel: CategorizeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val current = state.current

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        when {
            state.queue.isEmpty() -> {
                EmptyState(
                    icon = Icons.Filled.Sell,
                    title = "No transactions yet",
                    subtitle = "Sync SMS or add a transaction to start categorizing.",
                    modifier = Modifier.padding(innerPadding)
                )
            }

            current == null -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "All done!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Categorized ${state.assignedCount} of ${state.queue.size} transactions.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = viewModel::reset) {
                        Text("Start over")
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Categorize",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.index + 1} of ${state.queue.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    CategoryCard(
                        transaction = current,
                        bankName = state.banks.find { it.id == current.bankId }?.name ?: "Unknown",
                        categories = state.categories,
                        selectedCategoryId = current.categoryId,
                        onCategorySelected = viewModel::assignCategory
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = viewModel::skip) {
                            Text("Skip")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryCard(
    transaction: Transaction,
    bankName: String,
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = formatPaisa(transaction.amount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            DetailRow("Type", transaction.transactionType.name)
            DetailRow("Description", transaction.description)
            DetailRow("Bank", bankName)
            DetailRow(
                "Date",
                transaction.transactionDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
            )
            DetailRow(
                "Current",
                selectedCategory?.name ?: "Uncategorized"
            )

            Spacer(Modifier.height(4.dp))

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "Uncategorized",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            onCategorySelected(null)
                            categoryExpanded = false
                        }
                    )
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                onCategorySelected(cat.id)
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the categorizing tests**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.categorize.CategorizeViewModelTest" -v`
Expected: PASS (6 tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeScreen.kt
git commit -m "feat(categorize): add bulk categorize screen"
```

---

### Task 4: Full gate + docs

**Files:**
- Modify: `TESTING.md`
- Modify: `TODO.md`

**Interfaces:**
- Consumes: everything from Tasks 1–3.

- [ ] **Step 1: Run the full gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: ALL PASS, BUILD SUCCESSFUL. Baseline 332 + 6 (Task 2) = **338**. If your actual count differs, record the real number. (If the demo-data-gate plan landed first, baseline is 346 + 6 = 352.)

- [ ] **Step 2: Update TESTING.md**

Read the file first, then append the newest section after the demo-data-gate section (or after §12 if this plan lands alone). Use the file's existing numbering style:

```markdown
## 14. Bulk Categorize

A tap-through test plan. Every item is **Action → Expected result**.

- [ ] Bottom nav shows a **Categorize** tab (icon) between Transactions and Parser.
- [ ] With no transactions → tab shows the "No transactions yet" empty state.
- [ ] After loading demo data / syncing → tab shows a card: amount, type, description, bank, date, and the current category ("Uncategorized" for SMS transactions).
- [ ] Progress reads "1 of N".
- [ ] Uncategorized transactions appear BEFORE already-categorized ones.
- [ ] Open the category dropdown → pick **Food** → card advances to the next transaction; Transactions list shows the first one now categorized.
- [ ] Pick **None** in the dropdown → transaction becomes Uncategorized and the card advances.
- [ ] Tap **Skip** → card advances WITHOUT changing the category.
- [ ] Reach the end → "All done!" + "Categorized N of M transactions."; **Start over** returns to the first card.
- [ ] Categorizing works while demo data is present (no demo barrier here).
```

Update the summary table (ViewModels row + test count).

- [ ] **Step 3: Update TODO.md**

Read the file first and mark the bulk-categorize feature as shipped, matching its existing format, e.g. `- [x] Bulk categorize flow (Categorize tab: card-by-card category assignment)`.

- [ ] **Step 4: Commit**

```bash
git add TESTING.md TODO.md
git commit -m "docs: add bulk categorize to testing checklist and todo"
```
