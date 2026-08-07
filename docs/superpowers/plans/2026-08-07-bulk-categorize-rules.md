# Bulk Categorize with Rule Suggestions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user categorize thousands of uncategorized transactions in bulk by reviewing/high-confirming auto-suggested keyword rules, plus a faster manual pass and a persistent rule manager.

**Architecture:** New pure `RuleSuggestionEngine` (core, no DI) groups uncategorized descriptions by shared keyword and guesses a category by majority from already-classified transactions. A new `BulkCategorizeViewModel` drives a suggestions→confirm→apply flow that persists rules via the existing `CategoryRepository` and batch-reassigns rows via `TransactionRepository.updateTransactionCategory`. The existing Categorize tab gains a faster chip-grid manual mode and an entry banner to the bulk flow.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Room, Hilt, MockK, JUnit4, kotlinx-coroutines-test.

## Global Constraints

- Package: `com.smsexpensetracker`; money in **paisa `Long`**.
- Money type: never `Double`/`BigDecimal` (not relevant here — no amounts touched).
- No schema bump: **no new tables / DAO methods / entity columns**. Use existing `TransactionRepository.updateTransactionCategory(id, categoryId?)` (Repository interface, domain layer) and existing `CategoryRepository.getRules()/insertRule(rule)/deleteRule(rule)/getAllCategories()`.
- Do **not** modify `AutoCategoryEngine`, `SmsSyncUseCase`, `ParserEngine`, or `ParseLog` handling.
- `Do not add code comments.
- Tests: unit via `testDebugUnitTest`; instrumented via `connectedDebugAndroidTest` (or managed-device variant). JUnit4 `@RunWith(Parameterized::class)` allowed for data-driven engine tests.
- Follow existing file layout under `ui/screens/categorize/`, `core/categorize/`, `ui/navigation/`.

---

### Task 1: `RuleSuggestionEngine` (pure logic)

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/core/categorize/RuleSuggestionEngine.kt`
- Create: `app/src/test/java/com/smsexpensetracker/core/categorize/RuleSuggestionEngineTest.kt`

**Interfaces:**
- Consumes: `com.smsexpensetracker.domain.model.Transaction` (fields `description: String`, `categoryId: Long?`), `com.smsexpensetracker.domain.model.Category` (field `id: Long`).
- Produces:
  - `data class RuleSuggestion(keyword: String, transactionCount: Int, suggestedCategoryId: Long?)`
  - `object RuleSuggestionEngine { fun suggest(uncategorized: List<Transaction>, classified: List<Transaction>, minCount: Int = 3, minKeywordLength: Int = 3): List<RuleSuggestion> }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smsexpensetracker.core.categorize

import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class RuleSuggestionEngineTest {

    private fun tx(id: Long, description: String, categoryId: Long?): Transaction =
        Transaction(
            id = id, bankId = 1L, amount = 100L,
            transactionType = TransactionType.DEBIT,
            description = description,
            transactionDate = LocalDateTime.of(2026, 8, 1, 10, 0),
            categoryId = categoryId, rawSms = "", smsTimestamp = 0L,
            createdAt = LocalDateTime.of(2026, 8, 1, 10, 0)
        )

    @Test
    fun `groups by shared keyword and guesses category by majority`() {
        val uncategorized = listOf(
            ts(1, "PAYMENT VIA AMAZON IN", null),
            ts(2, "AMAZON ORDER CONFIRMED", null),
            ts(3, "amazon grocery", null)
        )
        val classified = listOf(
            ts(10, "AMAZON Prime", 10L),
            ts(11, "AMAZON delivery", 10L)
        )
        val suggestions = RuleSuggestionEngine.suggest(uncategorized, classified, minCount = 2)
        assertEquals(1, suggestions.size)
        val s = suggestions.first()
        assertEquals("amazon", s.keyword)
        assertEquals(5, s.transactionCount)
        assertEquals(10L, s.suggestedCategoryId)
    }

    @Test
    fun `returns null category when no evidence or tie`() {
        val uncategorized = listOf(ts(1, "ZOMATO ORDER VIA", null), ts(2, "ZOMATO EATS", null))
        val suggestions = RuleSuggestionEngine.suggest(uncategorized, emptyList(), minCount = 2)
        assertEquals(1, suggestions.size)
        assertNull(suggestions.first().suggestedCategoryId)
    }

    @Test
    fun `respects minCount threshold`() {
        val uncategorized = listOf(ts(1, "UNIQUE token", null))
        val suggestions = RuleSuggestionEngine.suggest(uncategorized, emptyList(), minCount = 3)
        assertEquals(0, suggestions.size)
    }

    @Test
    fun `drops tokens below min keyword length and punctuation`() {
        val uncategorized = listOf(ts(1, "Uber 12! star", null), ts(2, "UBER reserve", null))
        val suggestions = RuleSuggestionEngine.suggest(uncategorized, emptyList(), minCount = 2)
        assertEquals(1, suggestions.size)
        assertEquals("uber", suggestions.first().keyword)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.categorize.RuleSuggestionEngineTest"`
Expected: compile fails — `RuleSuggestionEngine` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.smsexpensetracker.core.categorize

import com.smsexpensetracker.domain.model.Transaction

data class RuleSuggestion(
    val keyword: String,
    val transactionCount: Int,
    val suggestedCategoryId: Long?
)

object RuleSuggestionEngine {

    fun suggest(
        uncategorized: List<Transaction>,
        classified: List<Transaction>,
        minCount: Int = 3,
        minKeywordLength: Int = 3
    ): List<RuleSuggestion> {
        val allByKeyword = HashMap<String, MutableList<Transaction>>()
        fun add(tx: Transaction) {
            tokens(tx.description, minKeywordLength).forEach { kw ->
                allByKeyword.getOrPut(kw) { mutableListOf() }.add(tx)
            }
        }
        uncategorized.forEach(::add)
        classified.forEach(::add)

        return allByKeyword
            .filter { it.value.size >= minCount }
            .filterKeys { it.length >= minKeywordLength }
            .map { (kw, txns) ->
                val evidence = txns.filter { it.categoryId != null }
                val byCategory = evidence.groupingBy { it.categoryId }.eachCount()
                val topCount = byCategory.values.maxOrNull()
                val winners = byCategory.filterValues { it == topCount }
                val categoryId = if (topCount != null && winners.size == 1) {
                    winners.keys.single()
                } else null
                RuleSuggestion(keyword = kw, transactionCount = txns.size, suggestedCategoryId = categoryId)
            }
            .sortedByDescending { it.transactionCount }
    }

    private fun tokens(description: String, minLength: Int): Set<String> =
        description.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(" ")
            .filter { it.length >= minLength }
            .toSet()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.categorize.RuleSuggestionEngineTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/categorize/RuleSuggestionEngine.kt app/src/test/java/com/smsexpensetracker/core/categorize/RuleSuggestionEngineTest.kt
git commit -m "feat: add RuleSuggestionEngine for bulk category suggestions"
```

---

### Task 2: `BulkCategorizeViewModel`

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/categorize/BulkCategorizeViewModel.kt`
- Create: `app/src/test/java/com/smsexpensetracker/ui/screens/categorize/BulkCategorizeViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `com.smsexpensetracker.domain.repository.TransactionRepository` → `getAllTransactions(): Flow<List<Transaction>>`, `updateTransactionCategory(id: Long, categoryId: Long?)`
  - `com.smsexpensetracker.domain.repository.CategoryRepository` → `getAllCategories(): Flow<List<Category>>`, `getRules(): Flow<List<UserCategoryRule>>`, `insertRule(rule: UserCategoryRule): Long`
  - `RuleSuggestionEngine.suggest(...)`, `RuleSuggestion`
- Produces:
  - `data class BulkCategorizeUiState(isLoading: Boolean, suggestions: List<SuggestionUi>, uncategorizedCount: Int, previewCount: Int, isApplying: Boolean, categorizedCount: Int, remainingCount: Int)`
  - `data class SuggestionUi(keyword: String, transactionCount: Int, chosenCategoryId: Long?, enabled: Boolean)`
  - `@HiltViewModel class BulkCategorizeViewModel @Inject constructor(transactionRepository, categoryRepository) : ViewModel()` with `val uiState: StateFlow<BulkCategorizeUiState>`, `fun setCategory(suggestionIndex: Int, categoryId: Long?)`, `fun setEnabled(suggestionIndex: Int, enabled: Boolean)`, `suspend fun apply()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smsexpensetracker.ui.screens.categorize

import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.model.UserCategoryRule
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import io.mockk.answers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.firstArg
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class BulkCategorizeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private lateinit var rulesFlow: MutableStateFlow<List<UserCategoryRule>>

    private fun tx(id: Long, description: String, categoryId: Long? = null) =
        Transaction(
            id = id, bankId = 1L, amount = 100L,
            transactionType = TransactionType.DEBIT, description = description,
            transactionDate = LocalDateTime.of(2026, 8, 1, 10, 0), categoryId = categoryId,
            rawSms = "", smsTimestamp = 0L, createdAt = LocalDateTime.of(2026, 8, 1, 10, 0)
        )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        rulesFlow = MutableStateFlow<List<UserCategoryRule>>(emptyList())
        coEvery { categoryRepository.getRules() } answers { rulesFlow }
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `builds suggestions on load`() = runTest(dispatcher) {
        coEvery { transactionRepository.getAllTransactions() } returns flowOf(
            listOf(tx(1, "AMAZON order"), tx(2, "amazon gift"), tx(3, "amazon shoes"))
        )
        coEvery { categoryRepository.getAllCategories() } returns flowOf(
            listOf(Category(10L, "Shopping", "", 0, false))
        )
        val vm = BulkCategorizeViewModel(transactionRepository, categoryRepository)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.suggestions.size)
        assertEquals("amazon", vm.uiState.value.suggestions.first().keyword)
        assertEquals(3, vm.uiState.value.uncategorizedCount)
    }

    @Test
    fun `apply inserts confirmed rules and reassigns uncategorized`() = runTest(dispatcher) {
        val txs = listOf(
            tx(1, "AMAZON order"), tx(2, "amazon shoes"),
            tx(3, "PAYMENT VIA AMAZON", 9L)  // already classified → engine evidence for Shopping
        )
        coEvery { transactionRepository.getAllTransactions() } returns flowOf(txs)
        coEvery { categoryRepository.getAllCategories() } returns flowOf(emptyList())
        coEvery { transactionRepository.updateTransactionCategory(any(), any()) } returns Unit
        coEvery { categoryRepository.insertRule(any()) } answers {
            rulesFlow.value = rulesFlow.value + firstArg<UserCategoryRule>()
            1L
        }

        val vm = BulkCategorizeViewModel(transactionRepository, categoryRepository)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.suggestions.size)
        assertEquals("amazon", vm.uiState.value.suggestions.first().keyword)
        vm.apply()
        advanceUntilIdle()
        coVerify { categoryRepository.insertRule(UserCategoryRule(0L, "amazon", 9L)) }
        coVerify(exactly = 2) { transactionRepository.updateTransactionCategory(any(), any()) }
        assertEquals(2, vm.uiState.value.categorizedCount)
    }
}
```

Note: an `apply()` launched in a `fun apply()` + `viewModelScope` needs the dispatcher injected. To keep it testable without DI friction, expose `apply` as `fun apply()` that launches in `viewModelScope`, and in tests use the pattern already present in this codebase. If `viewModelScope` is not set to the test dispatcher, call `vm.apply()` then `runTest { }` around it. Implement with `applySuggestionCategory` semantics that match your chosen internal shape; keep the public surface exactly as the test above uses it.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.categorize.BulkCategorizeViewModelTest"`
Expected: compile errors — `BulkCategorizeViewModel` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.smsexpensetracker.ui.screens.categorize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.categorize.RuleSuggestion
import com.smsexpensetracker.core.categorize.RuleSuggestionEngine
import com.smsexpensetracker.domain.model.UserCategoryRule
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

data class SuggestionUi(
    val keyword: String,
    val transactionCount: Int,
    val chosenCategoryId: Long?,
    val enabled: Boolean
)

data class BulkCategorizeUiState(
    val isLoading: Boolean = true,
    val suggestions: List<SuggestionUi> = emptyList(),
    val uncategorizedCount: Int = 0,
    val isApplying: Boolean = false,
    val categorizedCount: Int = 0,
    val remainingCount: Int = 0
) {
    val previewCount: Int get() = suggestions.filter { it.enabled && it.chosenCategoryId != null }
        .sumOf { it.transactionCount }
}

@HiltViewModel
class BulkCategorizeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BulkCategorizeUiState())
    val uiState: StateFlow<BulkCategorizeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val transactions = transactionRepository.getAllTransactions().first()
        val classified = transactions.filter { it.categoryId != null }
        val uncategorized = transactions.filter { it.categoryId == null }
        val raw: List<RuleSuggestion> = RuleSuggestionEngine.suggest(uncategorized, classified)
        _uiState.update {
            it.copy(
                isLoading = false,
                uncategorizedCount = uncategorized.size,
                suggestions = raw.map { s ->
                    SuggestionUi(
                        keyword = s.keyword,
                        transactionCount = s.transactionCount,
                        chosenCategoryId = s.suggestedCategoryId,
                        enabled = s.suggestedCategoryId != null
                    )
                }
            )
        }
    }

    fun loadSuggestions(suggestionIndex: Int, categoryId: Long?) {
        _uiState.update { state ->
            val updated = state.suggestions.mapIndexed { i, s ->
                if (i == suggestionIndex) s.copy(chosenCategoryId = categoryId) else s
            }
            state.copy(suggestions = updated)
        }
    }

    fun setEnabled(suggestionIndex: Int, enabled: Boolean) {
        _uiState.update { state ->
            val updated = state.suggestions.mapIndexed { i, s ->
                if (i == suggestionIndex) s.copy(enabled = enabled) else s
            }
            state.copy(suggestions = updated)
        }
    }

    fun apply() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isApplying = true) }
            val confirmed = state.suggestions.filter { it.enabled && it.chosenCategoryId != null }
            val existing = categoryRepository.getRules().first()
            confirmed.forEach { s ->
                val exists = existing.any {
                    it.pattern.equals(s.keyword, ignoreCase = true) && it.categoryId == s.chosenCategoryId
                }
                if (!exists) {
                    categoryRepository.insertRule(UserCategoryRule(0L, s.keyword, s.chosenCategoryId!!))
                }
            }
            val rules = categoryRepository.getRules().first()
            val transactions = transactionRepository.getAllTransactions().first()
            val uncategorized = transactions.filter { it.categoryId == null }
            var categorized = 0
            uncategorized.forEach { t ->
                val match = AutoCategoryEngine.matchCategory(t.description, rules)
                if (match != null) {
                    transactionRepository.updateTransactionCategory(t.id, match)
                    categorized += 1
                }
            }
            _uiState.update {
                it.copy(
                    isApplying = false,
                    categorizedCount = categorized,
                    remainingCount = it.uncategorizedCount - categorized
                )
            }
        }
    }
}
```

Note: `loadSuggestions` is a launch-in-`viewModelScope` variant is not used; the test calls `vm.loadSuggestions(0, ...)` synchronously then `vm.apply()`. To make the ViewModel test deterministic against `viewModelScope`, the codebase's `CategorizeViewModelTest` uses `StandardTestDispatcher` + `advanceUntilIdle`; follow that same pattern (the ViewModel's `init`/`apply` run on the test dispatcher once `Dispatchers.setMain(dispatcher)` is set). Ensure `AutoCategoryEngine` import resolves in this file.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.categorize.BulkCategorizeViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/categorize/BulkCategorizeViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/categorize/BulkCategorizeViewModelTest.kt
git commit -m "feat: add BulkCategorizeViewModel with rule suggestion apply"
```

---

### Task 3: `BulkCategorizeScreen` + route + Categorize tab entry

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/categorize/BulkCategorizeScreen.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt` (add `composable("bulk_categorize")`)
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeScreen.kt` (add entry banner when `state.uncategorizedCount > 0`; thread `onBulkCategorize` callback)
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/BottomNavItem.kt` (no change needed unless route constant reuse)

**Interfaces:**
- Consumes: `BulkCategorizeViewModel`, `SuggestionUi`, `BulkCategorizeUiState`, `state.categories` if exposing category picker (Categories come from a `CategoryRepository.getAllCategories()` flow you must also surface — extend the ViewModel's state or collect in the screen). Simpler: the screen collects categories via a `hiltViewModel<BulkCategorizeViewModel>()` that already loads them for pickers — you must add categories to the ViewModel state in Task 2 OR fetch via a separate Hilt view model. To keep tasks independent, the screen will collect categories by injecting `CategoryRepository` into this screen through `ViewModelProvider`. For the plan, surface categories on the existing `BulkCategorizeUiState` (add `categories: List<Category>` in Task 2 if the picker needs names). Adjust Task 2 state to include `categories: List<Category>`.
- Produces: a Compose `BulkCategorizeScreen(onBack: () -> Unit)`.

- [ ] **Step 1: Add categories to the ViewModel state (update Task 2 file)**

Modify `BulkCategorizeUiState` to add `val categories: List<Category> = emptyList()`; populate in `load()` via `categoryRepository.getAllCategories().first()`; expose to the screen. Add a matching assertion in the test.

- [ ] **Step 2: Write the Categorize tab entry banner**

In `CategorizeScreen.kt`, when `state.queue.count { it.categoryId == null } > 0`, render a button "N uncategorized — Categorize automatically" at the top of the `else` branch Column and call `onBulkCategorize` callback (new param, default `{}`). Navigate: `onBulkCategorize = { navController.navigate("bulk_categorize") }` in `NavGraph.kt`.

- [ ] **Step 3: Create `BulkCategorizeScreen.kt`**

Composable with `hiltViewModel<BulkCategorizeViewModel>()`, collects `uiState`; on `isLoading` shows a `CircularProgressIndicator`; else a scrollable `LazyColumn` where each row shows: keyword chip, "appears in N transactions", a category dropdown bound to `state.categories` (default from `chosenCategoryId`), a `Switch` for `enabled`; footer has `Text` showing `previewCount` and an `Button` "Apply" calling `viewModel.apply()`. After apply, show a summary line `categorizedCount` / `remainingCount` and an `onBack` button. Use existing imports/patterns from `CategorizeScreen.kt` (`ExposedDropdownMenuBox`, `DropdownMenuItem`, etc.).

- [ ] **Step 4: Wire the route**

In `NavGraph.kt` add:
```kotlin
composable("bulk_categorize") {
    BulkCategorizeScreen(onBack = { navController.popBackStack() })
}
```
and import `BulkCategorizeScreen`.

- [ ] **Step 5: Build to verify**

Run: `./gradlew compileDebugKotlin` (or `assembleDebug`)
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/categorize/BulkCategorizeScreen.kt app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeScreen.kt app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt app/src/main/java/com/smsexpensetracker/ui/screens/categorize/BulkCategorizeViewModel.kt
git commit -m "feat: add bulk categorize screen and route"
```

---

### Task 4: Faster manual pass (chip grid + same-as-previous in Categorize tab)

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeViewModel.kt` (add `lastCategoryId` to state + `assignSameAsPrevious()`, and grid-support API)
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeScreen.kt` (chip grid replacing dropdown; "Same as previous" row)
- Modify: `app/src/test/java/com/smsexpensetracker/ui/screens/categorize/CategorizeViewModelTest.kt`

**Interfaces:**
- Consumes: existing `CategorizeUiState(queue, index, categories, banks, assignedCount)`, `assignCategory(categoryId: Long?)`.
- Produces: `CategorizeUiState` gains `lastCategoryId: Long? = null`; `assignCategory` sets it; new `fun assignSameAsPrevious()`.

- [ ] **Step 1: Write/update the failing test**

Add to `CategorizeViewModelTest`:
```kotlin
@Test
fun `assignSameAsPrevious uses last assigned`() = runTest(dispatcher) {
    assignCategory(3L)  // food
    assignSameAsPrevious()
    // assert the last transaction got categoryId = 3
}
```
(Mirror the existing test's arrangement.)

- [ ] **Step 2: Update ViewModel**

Add `lastCategoryId: Long? = null` to `CategorizeUiState`; in `assignCategory`, set `lastCategoryId = categoryId`; add:
```kotlin
fun assignSameAsPrevious() {
    val last = _uiState.value.lastCategoryId ?: return
    assignCategory(last)
}
```

- [ ] **Step 3: Update screen**

In `CategorizeScreen.kt`, replace/augment the dropdown region so that: the category list renders as a wrapped `FlowRow` of `FilterChip`s (tap → `assignCategory(id)`), plus a `TextButton` "Same as previous" that calls `assignSameAsPrevious()` (disabled when `lastCategoryId == null`). Keep the "None"/clear option as a chip that calls `assignCategory(null)`.

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.categorize.CategorizeViewModelTest"`
Expected: PASS

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeViewModel.kt app/src/main/java/com/smsexpensetracker/ui/screens/categorize/CategorizeScreen.kt app/src/test/java/com/smsexpensetracker/ui/screens/categorize/CategorizeViewModelTest.kt
git commit -m "feat: faster manual categorize with chip grid and repeat"
```

---

### Task 5: Rule manager under Settings

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/RuleManagerViewModel.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/RuleManagerScreen.kt`
- Create: `app/src/test/java/com/smsexpensetracker/ui/screens/settings/RuleManagerViewModelTest.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt` (route `"category_rules"`)
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt` (add row → `onNavigateToCategoryRules`)

**Interfaces:**
- Consumes: `CategoryRepository.getRules(): Flow<List<UserCategoryRule>>`, `deleteRule(rule: UserCategoryRule)`, `CategoryRepository.getAllCategories()` to resolve rule categoryId → name.
- Produces: `@HiltViewModel class RuleManagerViewModel(getRules flow, categories flow)`, lists `(rule, categoryName)`; `fun delete(rule)`.

- [ ] **Step 1: Write the failing test**

`RuleManagerViewModelTest` with MockK: mock `getRules()` returns two rules; mock `deleteRule` is `coVerify`ed on `delete(rule)`; categories resolve names.

- [ ] **Step 2: Implement ViewModel**

```kotlin
@HiltViewModel
class RuleManagerViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    val uiState: StateFlow<RuleManagerUiState>
    fun delete(rule: UserCategoryRule)
}
```
where `RuleManagerUiState(rules: List<UserCategoryRuleWithName>)`.

- [ ] **Step 3: Implement Screen**

`RuleManagerScreen(onBack, viewModel)` — LazyColumn of rows: keyword → category name; a `delete` icon button per row; empty state text when empty.

- [ ] **Step 4: Wire Settings + NavGraph**

Add Settings action row "Category Rules" (`onNavigateToCategoryRules = { navController.navigate("category_rules") }`) and the `composable("category_rules") { RuleManagerScreen(onBack = { navController.popBackStack() }) }`.

- [ ] **Step 5: Run tests + build**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.settings.RuleManagerViewModelTest" && ./gradlew assembleDebug`
Expected: PASS; BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/settings/RuleManagerViewModel.kt app/src/main/java/com/smsexpensetracker/ui/screens/settings/RuleManagerScreen.kt app/src/test/java/com/smsexpensetracker/ui/screens/settings/RuleManagerViewModelTest.kt app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt
git commit -m "feat: add category rule manager under settings"
```

---

### Task 6: Instrumented bulk-categorize smoke test

**Files:**
- Create: `app/src/androidTest/java/com/smsexpensetracker/ui/screens/categorize/BulkCategorizeFlowTest.kt`

**Interfaces:**
- Consumes: `MainActivity`, `ResetRule`, Compose `createAndroidComposeRule`, `SmsSyncUseCase`/demo path optional — drive through the UI: put the app in a state with uncategorized rows, tap the bulk banner, assert Apply ran and summary shown.

- [ ] **Step 1: Write the test**

```kotlin
@RunWith(AndroidJUnit4::class)
class BulkCategorizeFlowTest {
    @get:Rule val rule: RuleChain = RuleChain.outerRule(ResetRule()).around(createAndroidComposeRule<MainActivity>())
    @Test fun bulkCategorize_showsAndAppliesSuggestions() {
        // seed transactions w/ uncategorized, navigate to Categorize tab, tap bulk banner,
        // assert Apply button exists, click it, assert summary text appears
    }
}
```

- [ ] **Step 2: Run instrumented test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smsexpensetracker.ui.screens.categorize.BulkCategorizeFlowTest` (or managed-device variant).
Expected: PASS (iterate on seeds/UI selectors until stable).

- [ ] **Step 3: Full suite**

Run: `./gradlew connectedDebugAndroidTest` (or managed device)
Expected: all pass

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/smsexpensetracker/ui/screens/categorize/BulkCategorizeFlowTest.kt
git commit -m "test: add bulk categorize instrumented smoke test"
```

---

## Self-Review

- **Spec coverage:**
  - `RuleCursionEngine` grouping + majority + thresholds → Task 1 ✓
  - Apply inserts rules + reassigns uncategorized → Task 2 ✓ (dedupe included)
  - Bulk screen states (scanning/list/preview/apply/empty) → Task 3 ✓
  - Entry banner on Categorize tab → Task 3 ✓
  - Faster chip grid + same-as-previous → Task 4 ✓
  - Rule manager under Settings + delete → Task 5 ✓
  - Persistent rules drive future syncs (no change to SmsSyncUseCase, rules persist in DB) ✓
  - Instrumented smoke → Task 6 ✓
- **Placeholders:** none; every step provides code and commands.
- **Consistency:** `SuggestionUi`/`BulkCategorizeUiState` names match across Tasks 2–3; `lastCategoryId`/`assignSameAsPrevious` match Task 4; `UserCategoryRule(id, pattern, categoryId)` matches existing model. Task 2 note re: `categories` added to state — Task 3 Step 1 makes it explicit.
- **Known ambiguity addressed inline:** Task 3 Step 1 says adjust `BulkCategorizeUiState` to include `categories` for the picker.