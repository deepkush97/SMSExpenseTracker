# Edit Transaction (Bottom-Sheet Editor) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the read-only transaction detail bottom sheet into a full editor so users can correct amount, type, date, bank, description, and category on any transaction, with the list refreshing live.

**Architecture:** Tap a row → `TransactionsViewModel.onTransactionClick` initializes an edit form in a new private `EditFormState` flow (folded into the existing `combine`); the user edits in `TransactionEditSheet` (evolves `TransactionDetailSheet.kt`); **Update** → `validateTransactionEdit` → `repository.updateEditedTransaction(Transaction)` → a **targeted** `TransactionDao.updateTransactionFields` `@Query` that touches only the six editable columns. `smsBodyHash`, `rawSms`, `smsTimestamp`, `createdAt`, `parseMethod` are untouched, so re-sync never duplicates an edited row. Room's `getAllTransactions()` flow re-emits and `combine()` rebuilds `uiState` — no manual refresh.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 `ModalBottomSheet`), Room + KSP, Hilt, JUnit 4 + MockK + `runTest`.

## Global Constraints

- Package root: `com.smsexpensetracker`.
- All amounts as **paisa** (`Long`) — never `Double`/`BigDecimal`. `parsePaisa` converts input; `formatPaisa` renders display strings.
- **Dedup invariant:** edits MUST go through the targeted `updateTransactionFields` `@Query`. A generic `@Update(entity)` would null `smsBodyHash` (`Transaction.toEntity()` drops it), and the next SMS sync would re-insert the same SMS as a duplicate. Guard with a `coVerify(exactly = 0) { transactionDao.update(any()) }` test.
- **`updateTransactionCategory` stays** on DAO/repository/interface — `CategorizeViewModel` still calls it. Only `TransactionsViewModel.onCategoryChange` and its test are removed.
- No code comments in new/changed code unless the task's code block explicitly includes them.
- No `lint`/`typecheck` configured — the gate is `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug` (KSP compiles the `@Query` SQL at build time, so a bad query fails the build).
- Commit directly to `main`; stage only the files listed in each task. Never stage the pre-existing dirty `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardViewModel.kt` or `opencode.json`.
- Test style: JUnit 4, MockK (`mockk`, `coEvery`/`coVerify` with `any()` matchers), `kotlinx-coroutines-test` `runTest(testDispatcher)` + `advanceUntilIdle()`; the baseline suite is **366 tests green**.

---

### Task 1: DAO + Repository (`updateTransactionFields` / `updateEditedTransaction`)

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt`
- Test: `app/src/test/java/com/smsexpensetracker/data/repository/TransactionRepositoryImplTest.kt`

**Interfaces:**
- Consumes: existing `Transaction` domain model, `TransactionEntity`, both `TransactionType` enums (domain + entity), `LocalDateTime`.
- Produces (later tasks depend on these exact signatures):
  - On `TransactionDao`:
    ```kotlin
    @Query("""
        UPDATE transactions
        SET bankId = :bankId, amount = :amount, type = :type,
            description = :description, transactionDate = :transactionDate,
            categoryId = :categoryId
        WHERE id = :id
    """)
    suspend fun updateTransactionFields(
        id: Long,
        bankId: Long,
        amount: Long,
        type: com.smsexpensetracker.core.database.entity.TransactionType,
        description: String,
        transactionDate: LocalDateTime,
        categoryId: Long?
    )
    ```
  - On `TransactionRepository` (domain interface):
    ```kotlin
    suspend fun updateEditedTransaction(transaction: Transaction)
    ```
  - `TransactionRepositoryImpl.updateEditedTransaction` maps `transaction.transactionType` via `com.smsexpensetracker.core.database.entity.TransactionType.valueOf(transaction.transactionType.name)` and calls `updateTransactionFields` with the 7 args.

- [ ] **Step 1: Write the failing tests** (append to `TransactionRepositoryImplTest.kt`, after `deleteAll delegates to the dao`):

```kotlin
    @Test
    fun `updateEditedTransaction maps enums and calls the targeted query`() = runTest {
        val domain = Transaction(
            id = 7L, bankId = 2L, amount = 125050L,
            transactionType = com.smsexpensetracker.domain.model.TransactionType.CREDIT,
            description = "Swiggy order", transactionDate = date, categoryId = 3L,
            rawSms = "raw", smsTimestamp = 123L, createdAt = date,
            parseMethod = DomainParseMethod.SMS
        )
        coEvery {
            transactionDao.updateTransactionFields(any(), any(), any(), any(), any(), any(), any())
        } returns Unit

        repo.updateEditedTransaction(domain)

        coVerify(exactly = 1) {
            transactionDao.updateTransactionFields(
                7L, 2L, 125050L, TransactionType.CREDIT, "Swiggy order", date, 3L
            )
        }
        coVerify(exactly = 0) { transactionDao.update(any<TransactionEntity>()) }
    }

    @Test
    fun `updateEditedTransaction passes null category when uncategorized`() = runTest {
        val domain = Transaction(
            id = 7L, bankId = 1L, amount = 10000L,
            transactionType = com.smsexpensetracker.domain.model.TransactionType.DEBIT,
            description = "Zomato", transactionDate = date, categoryId = null,
            rawSms = "raw", smsTimestamp = 123L, createdAt = date,
            parseMethod = DomainParseMethod.SMS
        )
        coEvery {
            transactionDao.updateTransactionFields(any(), any(), any(), any(), any(), any(), any())
        } returns Unit

        repo.updateEditedTransaction(domain)

        coVerify(exactly = 1) {
            transactionDao.updateTransactionFields(7L, 1L, 10000L, TransactionType.DEBIT, "Zomato", date, null)
        }
    }
```

(`Transaction`, `TransactionType` (entity), `TransactionEntity`, `DomainParseMethod`, `coEvery`, `coVerify`, `any`, `runTest`, `date` are already imported/defined in this test file.)

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.repository.TransactionRepositoryImplTest"`
Expected: compilation FAILURE — `updateTransactionFields`/`updateEditedTransaction` do not exist yet.

- [ ] **Step 3: Add the DAO `@Query`** (in `TransactionDao.kt`, after `updateTransactionCategory`; add `import java.time.LocalDateTime`):

```kotlin
    @Query("""
        UPDATE transactions
        SET bankId = :bankId, amount = :amount, type = :type,
            description = :description, transactionDate = :transactionDate,
            categoryId = :categoryId
        WHERE id = :id
    """)
    suspend fun updateTransactionFields(
        id: Long,
        bankId: Long,
        amount: Long,
        type: TransactionType,
        description: String,
        transactionDate: LocalDateTime,
        categoryId: Long?
    )
```

(`TransactionType` is already imported from the entity package in this file.)

- [ ] **Step 4: Add the repository interface method** (in `TransactionRepository.kt`, after `delete`):

```kotlin
    suspend fun updateEditedTransaction(transaction: Transaction)
```

- [ ] **Step 5: Implement the repository method** (in `TransactionRepositoryImpl.kt`, after `updateTransactionCategory`):

```kotlin
    override suspend fun updateEditedTransaction(transaction: Transaction) {
        transactionDao.updateTransactionFields(
            id = transaction.id,
            bankId = transaction.bankId,
            amount = transaction.amount,
            type = com.smsexpensetracker.core.database.entity.TransactionType.valueOf(transaction.transactionType.name),
            description = transaction.description,
            transactionDate = transaction.transactionDate,
            categoryId = transaction.categoryId
        )
    }
```

- [ ] **Step 6: Run the repository tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.repository.TransactionRepositoryImplTest"`
Expected: PASS (2 new, all existing). If the DAO `@Query` SQL is invalid, the KSP step fails the build — that is a genuine failure to fix, not noise.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt app/src/test/java/com/smsexpensetracker/data/repository/TransactionRepositoryImplTest.kt
git commit -m "feat(data): add targeted transaction edit update preserving dedup hash"
```

---

### Task 2: Pure helpers (`formatPaisaInput` + `validateTransactionEdit`)

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/util/AmountFormatter.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/util/AmountFormatterTest.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionEditValidation.kt`
- Create: `app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionEditValidationTest.kt`

**Interfaces:**
- Consumes: `parsePaisa` from `com.smsexpensetracker.core.parser` (regex `^(\d+)(?:\.(\d{1,2}))?$`, strips commas — rejects negatives as unparseable).
- Produces (later tasks depend on these exact names/types):
  - Top-level in `com.smsexpensetracker.ui.util`:
    ```kotlin
    fun formatPaisaInput(paisa: Long): String
    ```
    (`10050L` → `"100.50"`, `10000L` → `"100.00"`, `5L` → `"0.05"`; no `₹`, no grouping.)
  - In `com.smsexpensetracker.ui.screens.transactions`:
    ```kotlin
    data class EditFormErrors(val amount: String? = null, val description: String? = null)
    fun validateTransactionEdit(amountInput: String, description: String): EditFormErrors
    ```

- [ ] **Step 1: Write the failing tests** (add `AmountFormatterInputTest` class at the end of `AmountFormatterTest.kt`):

```kotlin
class AmountFormatterInputTest {

    @Test
    fun wholeRupeesKeepTrailingZeros() {
        assertEquals("100.00", formatPaisaInput(10000L))
    }

    @Test
    fun paisePadToTwoDigits() {
        assertEquals("1234.50", formatPaisaInput(123450L))
    }

    @Test
    fun singlePaiseRendersWithLeadingZero() {
        assertEquals("0.05", formatPaisaInput(5L))
    }
}
```

And create `app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionEditValidationTest.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionEditValidationTest {

    @Test
    fun `blank amount is rejected`() {
        assertEquals("Amount is required", validateTransactionEdit("  ", "Food").amount)
    }

    @Test
    fun `unparseable amount is rejected`() {
        assertEquals("Enter a valid amount", validateTransactionEdit("1.2.3", "Food").amount)
    }

    @Test
    fun `zero amount is rejected`() {
        assertEquals("Amount must be greater than zero", validateTransactionEdit("0", "Food").amount)
    }

    @Test
    fun `negative amount is rejected as invalid`() {
        assertEquals("Enter a valid amount", validateTransactionEdit("-5", "Food").amount)
    }

    @Test
    fun `blank description is rejected`() {
        assertEquals("Description is required", validateTransactionEdit("100", "   ").description)
    }

    @Test
    fun `overlong description is rejected`() {
        assertEquals(
            "Description must be 200 characters or fewer",
            validateTransactionEdit("100", "x".repeat(201)).description
        )
    }

    @Test
    fun `valid input has no errors`() {
        val errors = validateTransactionEdit("1,250.50", "Swiggy order")
        assertNull(errors.amount)
        assertNull(errors.description)
    }
}
```

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.util.AmountFormatterTest" --tests "com.smsexpensetracker.ui.screens.transactions.TransactionEditValidationTest"`
Expected: compilation FAILURE — `formatPaisaInput`, `EditFormErrors`, `validateTransactionEdit` do not exist yet.

- [ ] **Step 3: Add `formatPaisaInput`** (in `AmountFormatter.kt`, inside `object AmountFormatter`, after `formatAmountWithSign`):

```kotlin
    fun formatPaisaInput(paisa: Long): String {
        val abs = paisa.absoluteValue
        val rupees = abs / 100
        val paise = abs % 100
        return "$rupees.${paise.toString().padStart(2, '0')}"
    }
```

And the top-level alias next to the existing ones at the bottom of the file:

```kotlin
fun formatPaisaInput(paisa: Long): String = AmountFormatter.formatPaisaInput(paisa)
```

- [ ] **Step 4: Create `TransactionEditValidation.kt`**:

```kotlin
package com.smsexpensetracker.ui.screens.transactions

import com.smsexpensetracker.core.parser.parsePaisa

data class EditFormErrors(
    val amount: String? = null,
    val description: String? = null
)

fun validateTransactionEdit(amountInput: String, description: String): EditFormErrors {
    val amountPaisa = parsePaisa(amountInput)
    return EditFormErrors(
        amount = when {
            amountInput.isBlank() -> "Amount is required"
            amountPaisa == null -> "Enter a valid amount"
            amountPaisa <= 0 -> "Amount must be greater than zero"
            else -> null
        },
        description = when {
            description.isBlank() -> "Description is required"
            description.length > 200 -> "Description must be 200 characters or fewer"
            else -> null
        }
    )
}
```

- [ ] **Step 5: Run the new tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.util.AmountFormatterTest" --tests "com.smsexpensetracker.ui.screens.transactions.TransactionEditValidationTest"`
Expected: PASS (10 new).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/util/AmountFormatter.kt app/src/test/java/com/smsexpensetracker/ui/util/AmountFormatterTest.kt app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionEditValidation.kt app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionEditValidationTest.kt
git commit -m "feat(ui): add edit-form validation and plain amount input formatter"
```

---

### Task 3: ViewModel — edit form state + `updateTransaction()`

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt`
- Modify: `app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModelTest.kt`

**Interfaces:**
- Consumes: `updateEditedTransaction(transaction: Transaction)` (Task 1); `EditFormErrors`, `validateTransactionEdit` (Task 2); `formatPaisaInput` (Task 2); `parsePaisa` (existing).
- Produces — new `TransactionsUiState` fields (Task 4 reads these exact names):
  `editAmountInput: String`, `editType: TransactionType`, `editDateTime: LocalDateTime?`, `editBankId: Long?`, `editDescription: String`, `editCategoryId: Long?`, `editErrors: EditFormErrors`, `isUpdating: Boolean`, `showEditSavedSnackbar: Boolean`, `editSaveError: String?`
- Produces — methods (Task 4 wires these via `viewModel::`): `onEditAmountChange(String)`, `onEditTypeChange(TransactionType)`, `onEditDateChange(LocalDate)`, `onEditBankChange(Long)`, `onEditDescriptionChange(String)`, `onEditCategoryChange(Long?)`, `updateTransaction()`, `consumeEditSavedSnackbar()`, `consumeEditSaveError()`. `onTransactionClick(Transaction)` and `onDismissSheet()` change behavior. **`onCategoryChange(Long, Long?)` is deleted.**

- [ ] **Step 1: Write the failing tests** — replace the `onCategoryChange calls repository` test (lines 157-169) with six new tests; add `import org.junit.Assert.assertEquals` to the test file:

```kotlin
    @Test
    fun `onTransactionClick initializes edit form from transaction`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onTransactionClick(
            mockTransaction(
                id = 1L, bankId = 2L, amount = 10050L, type = TransactionType.CREDIT,
                description = "Zomato", transactionDate = LocalDateTime.of(2026, 1, 15, 10, 30),
                categoryId = 3L
            )
        )
        advanceUntilIdle()

        val s = viewModel.uiState.value
        assertEquals("100.50", s.editAmountInput)
        assertEquals(TransactionType.CREDIT, s.editType)
        assertEquals(LocalDateTime.of(2026, 1, 15, 10, 30), s.editDateTime)
        assertEquals(2L, s.editBankId)
        assertEquals("Zomato", s.editDescription)
        assertEquals(3L, s.editCategoryId)
    }

    @Test
    fun `valid updateTransaction persists edits and dismisses sheet`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        coEvery { transactionRepository.updateEditedTransaction(any()) } returns Unit
        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onTransactionClick(
            mockTransaction(
                id = 1L, amount = 10000L, type = TransactionType.DEBIT, description = "Old",
                transactionDate = LocalDateTime.of(2026, 1, 15, 10, 30)
            )
        )
        advanceUntilIdle()
        viewModel.onEditAmountChange("1250.50")
        viewModel.onEditTypeChange(TransactionType.CREDIT)
        viewModel.onEditDescriptionChange("New desc")
        viewModel.onEditBankChange(2L)
        viewModel.onEditCategoryChange(3L)
        viewModel.updateTransaction()
        advanceUntilIdle()

        coVerify {
            transactionRepository.updateEditedTransaction(
                match<Transaction> {
                    it.id == 1L && it.amount == 125050L &&
                        it.transactionType == TransactionType.CREDIT &&
                        it.description == "New desc" &&
                        it.bankId == 2L &&
                        it.categoryId == 3L
                }
            )
        }
        assertTrue(viewModel.uiState.value.selectedTransaction == null)
        assertTrue(viewModel.uiState.value.showEditSavedSnackbar)
    }

    @Test
    fun `invalid amount on update sets error and does not call repository`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        coEvery { transactionRepository.updateEditedTransaction(any()) } returns Unit
        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        viewModel.onTransactionClick(mockTransaction())
        advanceUntilIdle()

        viewModel.onEditAmountChange("0")
        viewModel.updateTransaction()
        advanceUntilIdle()

        assertEquals("Amount must be greater than zero", viewModel.uiState.value.editErrors.amount)
        coVerify(exactly = 0) { transactionRepository.updateEditedTransaction(any()) }
    }

    @Test
    fun `blank description on update sets error and does not call repository`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        coEvery { transactionRepository.updateEditedTransaction(any()) } returns Unit
        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        viewModel.onTransactionClick(mockTransaction())
        advanceUntilIdle()

        viewModel.onEditDescriptionChange("   ")
        viewModel.updateTransaction()
        advanceUntilIdle()

        assertEquals("Description is required", viewModel.uiState.value.editErrors.description)
        coVerify(exactly = 0) { transactionRepository.updateEditedTransaction(any()) }
    }

    @Test
    fun `updateTransaction opens demo barrier instead of updating when demo data present`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        coEvery { transactionRepository.updateEditedTransaction(any()) } returns Unit
        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        advanceUntilIdle()
        viewModel.onTransactionClick(mockTransaction())
        advanceUntilIdle()

        viewModel.updateTransaction()
        advanceUntilIdle()

        assertTrue(viewModel.showDemoBarrier.value)
        coVerify(exactly = 0) { transactionRepository.updateEditedTransaction(any()) }
    }

    @Test
    fun `failed update sets error and keeps sheet open`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        coEvery { transactionRepository.updateEditedTransaction(any()) } throws RuntimeException("boom")
        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        viewModel.onTransactionClick(mockTransaction())
        advanceUntilIdle()

        viewModel.updateTransaction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.selectedTransaction != null)
        assertEquals("Could not update transaction. Please try again.", viewModel.uiState.value.editSaveError)
        assertFalse(viewModel.uiState.value.isUpdating)
    }

    @Test
    fun `onDismissSheet clears selection and resets edit form`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        viewModel.onTransactionClick(mockTransaction(amount = 2500L, description = "Cafe"))
        advanceUntilIdle()
        viewModel.onEditAmountChange("999.99")

        viewModel.onDismissSheet()
        advanceUntilIdle()

        val s = viewModel.uiState.value
        assertTrue(s.selectedTransaction == null)
        assertEquals("", s.editAmountInput)
        assertEquals("", s.editDescription)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.transactions.TransactionsViewModelTest"`
Expected: compilation FAILURE — the new state fields/methods don't exist, and `onCategoryChange` still exists.

- [ ] **Step 3: Add imports** (to `TransactionsViewModel.kt`):

```kotlin
import com.smsexpensetracker.core.parser.parsePaisa
import com.smsexpensetracker.ui.util.formatPaisaInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
```

- [ ] **Step 4: Add the private `EditFormState` + flow and the new `uiState` fields**

Add `EditFormState` immediately after the `TransactionsUiState` data class (same file, top level):

```kotlin
private data class EditFormState(
    val amountInput: String = "",
    val type: TransactionType = TransactionType.DEBIT,
    val dateTime: LocalDateTime? = null,
    val bankId: Long? = null,
    val description: String = "",
    val categoryId: Long? = null,
    val errors: EditFormErrors = EditFormErrors(),
    val isUpdating: Boolean = false,
    val showSavedSnackbar: Boolean = false,
    val saveError: String? = null
)
```

Add these fields to `TransactionsUiState` (append after `syncMessage`, keeping the existing defaults):

```kotlin
    val editAmountInput: String = "",
    val editType: TransactionType = TransactionType.DEBIT,
    val editDateTime: LocalDateTime? = null,
    val editBankId: Long? = null,
    val editDescription: String = "",
    val editCategoryId: Long? = null,
    val editErrors: EditFormErrors = EditFormErrors(),
    val isUpdating: Boolean = false,
    val showEditSavedSnackbar: Boolean = false,
    val editSaveError: String? = null
```

Add the private flow next to `_selectedTransaction`:

```kotlin
    private val _editForm = MutableStateFlow(EditFormState())
```

- [ ] **Step 5: Fold `_editForm` into the `combine`**

Change the `combine(...)` call: add `_editForm` as the 10th flow argument (after `_syncMessage`). Inside the transform, after `val syncMessage = array[8] as String?`, add:

```kotlin
        val edit = array[9] as EditFormState
```

Then in the `TransactionsUiState(...)` construction add:

```kotlin
            editAmountInput = edit.amountInput,
            editType = edit.type,
            editDateTime = edit.dateTime,
            editBankId = edit.bankId,
            editDescription = edit.description,
            editCategoryId = edit.categoryId,
            editErrors = edit.errors,
            isUpdating = edit.isUpdating,
            showEditSavedSnackbar = edit.showSavedSnackbar,
            editSaveError = edit.saveError
```

- [ ] **Step 6: Replace `onTransactionClick`, `onDismissSheet`, and `onCategoryChange`; add the edit setters + `updateTransaction`**

Replace the existing block (lines 160-167):

```kotlin
    fun onTransactionClick(tx: Transaction) { _selectedTransaction.value = tx }
    fun onDismissSheet() { _selectedTransaction.value = null }

    fun onCategoryChange(transactionId: Long, categoryId: Long?) {
        viewModelScope.launch {
            transactionRepository.updateTransactionCategory(transactionId, categoryId)
        }
    }
```

with:

```kotlin
    fun onTransactionClick(tx: Transaction) {
        _selectedTransaction.value = tx
        _editForm.value = EditFormState(
            amountInput = formatPaisaInput(tx.amount),
            type = tx.transactionType,
            dateTime = tx.transactionDate,
            bankId = tx.bankId,
            description = tx.description,
            categoryId = tx.categoryId
        )
    }

    fun onDismissSheet() {
        _selectedTransaction.value = null
        _editForm.value = EditFormState()
    }

    fun onEditAmountChange(value: String) = _editForm.update {
        it.copy(
            amountInput = value.filter { c -> c.isDigit() || c == '.' || c == ',' },
            errors = it.errors.copy(amount = null)
        )
    }

    fun onEditTypeChange(type: TransactionType) = _editForm.update { it.copy(type = type) }

    fun onEditDateChange(date: LocalDate) = _editForm.update {
        it.copy(dateTime = it.dateTime?.with(date) ?: date.atStartOfDay())
    }

    fun onEditBankChange(id: Long) = _editForm.update { it.copy(bankId = id) }

    fun onEditDescriptionChange(value: String) = _editForm.update {
        it.copy(description = value, errors = it.errors.copy(description = null))
    }

    fun onEditCategoryChange(id: Long?) = _editForm.update { it.copy(categoryId = id) }

    fun updateTransaction() {
        val tx = _selectedTransaction.value ?: return
        val form = _editForm.value
        if (form.isUpdating) return
        if (_demoDataLoaded.value) {
            _showDemoBarrier.value = true
            return
        }

        val errors = validateTransactionEdit(form.amountInput, form.description)
        if (errors.amount != null || errors.description != null) {
            _editForm.update { it.copy(errors = errors) }
            return
        }

        val bankId = form.bankId ?: return
        val amount = parsePaisa(form.amountInput) ?: return
        val dateTime = form.dateTime ?: tx.transactionDate

        _editForm.update { it.copy(isUpdating = true) }
        viewModelScope.launch {
            try {
                transactionRepository.updateEditedTransaction(
                    tx.copy(
                        bankId = bankId,
                        amount = amount,
                        transactionType = form.type,
                        description = form.description.trim(),
                        transactionDate = dateTime,
                        categoryId = form.categoryId
                    )
                )
                _selectedTransaction.value = null
                _editForm.update {
                    it.copy(
                        amountInput = "",
                        type = TransactionType.DEBIT,
                        dateTime = null,
                        bankId = null,
                        description = "",
                        categoryId = null,
                        errors = EditFormErrors(),
                        isUpdating = false,
                        showSavedSnackbar = true,
                        saveError = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _editForm.update {
                    it.copy(isUpdating = false, saveError = "Could not update transaction. Please try again.")
                }
            }
        }
    }

    fun consumeEditSavedSnackbar() = _editForm.update { it.copy(showSavedSnackbar = false) }

    fun consumeEditSaveError() = _editForm.update { it.copy(saveError = null) }
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.transactions.TransactionsViewModelTest"`
Expected: PASS (6 new, 1 removed — `onCategoryChange calls repository` is gone). If an existing `uiState`-reading test fails, it is because the `combine`/`stateIn` emission shape changed — check the new `array[9]` access is correct and `advanceUntilIdle()` runs.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModelTest.kt
git commit -m "feat(transactions): add edit form state and update flow to viewmodel"
```

---

### Task 4: Editable bottom sheet + screen wiring

**Files:**
- Rename: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionDetailSheet.kt` → `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionEditSheet.kt` (full rewrite)
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt`

**Interfaces:**
- Consumes: the `TransactionsUiState` edit fields and VM methods from Task 3; `EditFormErrors` from Task 2. `onTransactionClick`/`onDismissSheet` are unchanged from the caller's perspective.
- Produces: `@Composable fun TransactionEditSheet(transaction: Transaction, amountInput: String, type: TransactionType, dateTime: LocalDateTime, bankId: Long?, description: String, categoryId: Long?, errors: EditFormErrors, isUpdating: Boolean, banks: List<Bank>, categories: List<Category>, onAmountChange: (String) -> Unit, onTypeChange: (TransactionType) -> Unit, onDateChange: (LocalDate) -> Unit, onBankChange: (Long) -> Unit, onDescriptionChange: (String) -> Unit, onCategoryChange: (Long?) -> Unit, onUpdate: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier)`.

> There are no Compose-UI unit tests in this repo (see TESTING.md "Not covered" row). The deliverable is verified by the build gate (`assembleDebug` catches signature/import errors) plus the manual smoke in Task 5.

- [ ] **Step 1: Rename the file**

Run: `git mv app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionDetailSheet.kt app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionEditSheet.kt`

- [ ] **Step 2: Rewrite `TransactionEditSheet.kt`** (full file — no comments, mirrors `ManualEntryScreen` field patterns, keeps `ModalBottomSheet` + `skipPartiallyExpanded = true`):

```kotlin
package com.smsexpensetracker.ui.screens.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditSheet(
    transaction: Transaction,
    amountInput: String,
    type: TransactionType,
    dateTime: LocalDateTime,
    bankId: Long?,
    description: String,
    categoryId: Long?,
    errors: EditFormErrors,
    isUpdating: Boolean,
    banks: List<Bank>,
    categories: List<Category>,
    onAmountChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onBankChange: (Long) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var bankExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateTime.toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    )

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
            Text("Edit Transaction", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = amountInput,
                onValueChange = onAmountChange,
                label = { Text("Amount") },
                leadingIcon = { Text("₹") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = errors.amount != null,
                supportingText = errors.amount?.let { { Text(it) } },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TransactionType.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = type == entry,
                        onClick = { onTypeChange(entry) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TransactionType.entries.size
                        )
                    ) {
                        Text(entry.name)
                    }
                }
            }

            OutlinedTextField(
                value = dateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            )

            ExposedDropdownMenuBox(
                expanded = bankExpanded,
                onExpandedChange = { bankExpanded = it }
            ) {
                OutlinedTextField(
                    value = banks.find { it.id == bankId }?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Bank") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankExpanded) },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = bankExpanded,
                    onDismissRequest = { bankExpanded = false }
                ) {
                    banks.forEach { bank ->
                        DropdownMenuItem(
                            text = { Text(bank.name) },
                            onClick = { onBankChange(bank.id); bankExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Description") },
                singleLine = true,
                isError = errors.description != null,
                supportingText = errors.description?.let { { Text(it) } },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = categories.find { it.id == categoryId }?.name ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = { onCategoryChange(null); categoryExpanded = false }
                    )
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = { onCategoryChange(cat.id); categoryExpanded = false }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isUpdating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onUpdate,
                    enabled = !isUpdating && errors.amount == null && errors.description == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isUpdating) "Updating..." else "Update")
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateChange(
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
```

- [ ] **Step 3: Wire the screen** — in `TransactionsScreen.kt`:

(a) Replace the `TransactionDetailSheet(...)` invocation block (lines 275-283) with:

```kotlin
    state.selectedTransaction?.let { tx ->
        TransactionEditSheet(
            transaction = tx,
            amountInput = state.editAmountInput,
            type = state.editType,
            dateTime = state.editDateTime ?: tx.transactionDate,
            bankId = state.editBankId,
            description = state.editDescription,
            categoryId = state.editCategoryId,
            errors = state.editErrors,
            isUpdating = state.isUpdating,
            banks = state.banks,
            categories = state.categories,
            onAmountChange = viewModel::onEditAmountChange,
            onTypeChange = viewModel::onEditTypeChange,
            onDateChange = viewModel::onEditDateChange,
            onBankChange = viewModel::onEditBankChange,
            onDescriptionChange = viewModel::onEditDescriptionChange,
            onCategoryChange = viewModel::onEditCategoryChange,
            onUpdate = viewModel::updateTransaction,
            onDismiss = viewModel::onDismissSheet
        )
    }
```

(b) Add two snackbar effects after the existing `LaunchedEffect(state.syncMessage)` block:

```kotlin
    LaunchedEffect(state.showEditSavedSnackbar) {
        if (state.showEditSavedSnackbar) {
            snackbarHostState.showSnackbar("Transaction updated")
            viewModel.consumeEditSavedSnackbar()
        }
    }

    LaunchedEffect(state.editSaveError) {
        val error = state.editSaveError
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.consumeEditSaveError()
        }
    }
```

- [ ] **Step 4: Run the full gate to verify build + no regressions**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL; **383 tests** pass (366 baseline + 2 Task 1 + 10 Task 2 + 6 Task 3, − 1 removed `onCategoryChange` test). Record the real measured count — the plan's numbers are estimates and TESTING.md must show the measured value.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionEditSheet.kt app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionDetailSheet.kt app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt
git commit -m "feat(transactions): editable transaction bottom sheet with update flow"
```

---

### Task 5: Bookend — docs + final gate

**Files:**
- Modify: `TESTING.md`
- Modify: `TODO.md`

- [ ] **Step 1: Mark the TODO item done**

In `TODO.md`, change line 117 from `- [ ] **Edit transaction details**` to `- [x] **Edit transaction details**` (keep the rest of the line unchanged).

- [ ] **Step 2: Update `TESTING.md` §4 (Transactions List)**

Replace the two rows at lines 48-49:

```
- [ ] Tap a transaction row. → A **bottom sheet** opens: read-only Amount/Type/Bank/Date/Description.
- [ ] In the sheet, tap the **Category dropdown** and pick a category, then close the sheet. → The change is saved (it appears in the Dashboard category breakdown shortly after).
```

with:

```
- [ ] Tap a transaction row. → A **bottom sheet** opens: editable Amount, Type (Credit/Debit), Date, Bank, Description, and Category, with **Cancel** / **Update** buttons.
- [ ] Edit **every field** (amount, type, date, bank, description, category), then tap **Update**. → Snackbar "Transaction updated"; the sheet closes; the list row and the Dashboard reflect the new values.
- [ ] Tap **Update** with a blank/zero amount or a blank description. → Inline error under the offending field; **Update** stays disabled until it is fixed.
- [ ] Tap **Cancel** after changing fields. → Edits are discarded; the sheet closes; the row is unchanged.
- [ ] With demo data loaded, tap **Update** on a transaction. → Demo-data barrier dialog appears; nothing is written.
- [ ] **Dedup safety [M]:** edit an SMS-derived transaction (from `push_test_sms.sh`), then re-run sync. → The edited row updates; no duplicate is inserted (`smsBodyHash` is preserved).
```

- [ ] **Step 3: Update the `TESTING.md` summary table** (last section, line 186+)

- Change the table header cell `What the 366 tests cover` → `What the <measured> tests cover` (use the count from Task 4 Step 4).
- In the **ViewModels** row, add: `, transaction edit form init/update/validation/demo-gate`.
- Add to the **Validation** row (or a new line next to it): `TransactionEditValidationTest` — `validateTransactionEdit` error strings, `AmountFormatterInputTest` — `formatPaisaInput` plain input strings.

- [ ] **Step 4: Run the final gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL; all tests pass; count matches what was recorded in Step 3.

- [ ] **Step 5: Commit**

```bash
git add TESTING.md TODO.md
git commit -m "docs: mark edit transaction done and add manual QA rows"
```

---

## Final Review Checklist (run after Task 5)

- [ ] `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug` is green with the measured test count recorded in TESTING.md.
- [ ] `TransactionDao` exposes only the targeted `updateTransactionFields`; no new `@Update`-based path was introduced for edits.
- [ ] `TransactionRepository.updateTransactionCategory` still exists and `CategorizeViewModel` still compiles/calls it.
- [ ] `TransactionsViewModel` no longer has `onCategoryChange`; `TransactionsScreen` no longer references `TransactionDetailSheet`.
- [ ] Manual smoke (emulator): edit every field → list + Dashboard reflect it; edit an SMS-derived row → re-sync produces no duplicate.
- [ ] Working tree clean except the pre-existing dirty `DashboardViewModel.kt` / `opencode.json`.
