package com.smsexpensetracker.ui.screens.transactions

import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.data.demo.DemoDataSeeder
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import com.smsexpensetracker.domain.usecase.GetTransactionsUseCase
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import com.smsexpensetracker.domain.value.SyncProgress
import com.smsexpensetracker.domain.value.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getTransactionsUseCase: GetTransactionsUseCase
    private lateinit var bankRepository: BankRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var smsSyncUseCase: SmsSyncUseCase
    private lateinit var demoDataPreferences: DemoDataPreferences
    private lateinit var demoDataSeeder: DemoDataSeeder
    private val demoDataLoadedFlow = MutableStateFlow(false)
    private lateinit var viewModel: TransactionsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getTransactionsUseCase = mockk()
        bankRepository = mockk()
        categoryRepository = mockk()
        transactionRepository = mockk()
        smsSyncUseCase = mockk()
        demoDataPreferences = mockk()
        demoDataSeeder = mockk()
        every { demoDataPreferences.demoDataLoaded } returns demoDataLoadedFlow
        every { smsSyncUseCase.progress } returns MutableStateFlow(SyncProgress())
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

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        viewModel.onMonthChange(YearMonth.of(2026, 1))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.displayedTransactions.size == 1)
        assertTrue(viewModel.uiState.value.displayedTransactions[0].id == janTx.id)
    }

    @Test
    fun `search query filters by description`() = runTest(testDispatcher) {
        val tx1 = mockTransaction(description = "Zomato order", transactionDate = LocalDateTime.now())
        val tx2 = mockTransaction(description = "Swiggy order", transactionDate = LocalDateTime.now())
        val txFlow = MutableStateFlow(listOf(tx1, tx2))

        coEvery { getTransactionsUseCase() } returns txFlow
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        viewModel.onSearchQueryChange("Zomato")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.displayedTransactions.size == 1)
        assertTrue(viewModel.uiState.value.displayedTransactions[0].id == tx1.id)
    }

    @Test
    fun `filter type limits displayed transactions`() = runTest(testDispatcher) {
        val credit = mockTransaction(type = TransactionType.CREDIT, transactionDate = LocalDateTime.now())
        val debit = mockTransaction(type = TransactionType.DEBIT, transactionDate = LocalDateTime.now())
        val txFlow = MutableStateFlow(listOf(credit, debit))

        coEvery { getTransactionsUseCase() } returns txFlow
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        viewModel.onFilterTypeChange(TransactionType.CREDIT)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.displayedTransactions.size == 1)
        assertTrue(viewModel.uiState.value.displayedTransactions[0].transactionType == TransactionType.CREDIT)
    }

    @Test
    fun `month navigation cannot go to future months`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val futureMonth = YearMonth.now().plusMonths(1)
        viewModel.onMonthChange(futureMonth)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.currentMonth != futureMonth)
    }

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

        val s = viewModel.uiState.value
        assertEquals("", s.editAmountInput)
        assertEquals(TransactionType.DEBIT, s.editType)
        assertNull(s.editDateTime)
        assertNull(s.editBankId)
        assertEquals("", s.editDescription)
        assertNull(s.editCategoryId)
        assertFalse(s.isUpdating)
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
    fun `second updateTransaction while update in flight does not launch a second repository call`() =
        runTest(testDispatcher) {
            coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
            every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
            every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
            coEvery { transactionRepository.updateEditedTransaction(any()) } coAnswers { delay(1000) }
            viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onTransactionClick(mockTransaction())
            advanceUntilIdle()

            viewModel.updateTransaction()
            runCurrent()
            viewModel.updateTransaction()
            advanceUntilIdle()

            coVerify(exactly = 1) { transactionRepository.updateEditedTransaction(any()) }
        }

    @Test
    fun `dismissing sheet while update in flight cancels update and skips completion`() =
        runTest(testDispatcher) {
            coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
            every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
            every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
            coEvery { transactionRepository.updateEditedTransaction(any()) } coAnswers { delay(1000) }
            viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onTransactionClick(mockTransaction())
            advanceUntilIdle()
            viewModel.updateTransaction()
            runCurrent()
            assertTrue(viewModel.uiState.value.isUpdating)

            viewModel.onDismissSheet()
            advanceUntilIdle()

            val s = viewModel.uiState.value
            assertTrue(s.selectedTransaction == null)
            assertFalse(s.isUpdating)
            assertFalse(s.showEditSavedSnackbar)
            assertEquals("", s.editAmountInput)
            assertEquals("", s.editDescription)
            coVerify(exactly = 1) { transactionRepository.updateEditedTransaction(any()) }
        }

    @Test
    fun `consumeEditSavedSnackbar clears the saved snackbar flag`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        coEvery { transactionRepository.updateEditedTransaction(any()) } returns Unit
        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        viewModel.onTransactionClick(mockTransaction())
        advanceUntilIdle()
        viewModel.updateTransaction()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditSavedSnackbar)

        viewModel.consumeEditSavedSnackbar()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEditSavedSnackbar)
    }

    @Test
    fun `consumeEditSaveError clears the edit save error`() = runTest(testDispatcher) {
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
        assertEquals("Could not update transaction. Please try again.", viewModel.uiState.value.editSaveError)

        viewModel.consumeEditSaveError()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editSaveError)
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

    @Test
    fun `sync runs use case and publishes result message`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        coEvery { smsSyncUseCase.sync() } returns SyncResult(scanned = 5, inserted = 2, unparsed = 1)

        viewModel = TransactionsViewModel(
            getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase,
            demoDataPreferences, demoDataSeeder
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.sync()
        advanceUntilIdle()

        val msg = viewModel.uiState.value.syncMessage
        assertTrue(msg != null && msg.contains("Scanned 5"))
        assertTrue(!viewModel.uiState.value.isSyncing)

        viewModel.consumeSyncMessage()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.syncMessage == null)
    }

    @Test
    fun `sync opens demo barrier instead of syncing when demo data present`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        viewModel = TransactionsViewModel(
            getTransactionsUseCase, bankRepository, categoryRepository,
            transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder
        )
        advanceUntilIdle()

        viewModel.sync()
        advanceUntilIdle()

        assertTrue(viewModel.showDemoBarrier.value)
        coVerify(exactly = 0) { smsSyncUseCase.sync() }
    }

    @Test
    fun `confirmDeleteDemoData deletes demo and closes barrier`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        viewModel = TransactionsViewModel(
            getTransactionsUseCase, bankRepository, categoryRepository,
            transactionRepository, smsSyncUseCase, demoDataPreferences, demoDataSeeder
        )
        advanceUntilIdle()
        coEvery { demoDataSeeder.deleteDemoData() } returns Unit

        viewModel.confirmDeleteDemoData()
        advanceUntilIdle()

        coVerify(exactly = 1) { demoDataSeeder.deleteDemoData() }
        assertFalse(viewModel.showDemoBarrier.value)
    }

    @Test
    fun `sync progress from use case surfaces while syncing and clears after`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        every { smsSyncUseCase.progress } returns MutableStateFlow(SyncProgress(processed = 25, total = 100, unparsed = 2))
        val gate = CompletableDeferred<SyncResult>()
        coEvery { smsSyncUseCase.sync() } coAnswers { gate.await() }

        viewModel = TransactionsViewModel(
            getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase,
            demoDataPreferences, demoDataSeeder
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.sync()
        advanceUntilIdle()

        val syncing = viewModel.uiState.value
        assertTrue(syncing.isSyncing)
        assertEquals(25, syncing.syncProgress?.processed)
        assertEquals(100, syncing.syncProgress?.total)
        assertEquals(2, syncing.syncProgress?.unparsed)

        gate.complete(SyncResult(scanned = 5, inserted = 2, unparsed = 1))
        advanceUntilIdle()

        val done = viewModel.uiState.value
        assertTrue(!done.isSyncing)
        assertNull(done.syncProgress)
    }
}
