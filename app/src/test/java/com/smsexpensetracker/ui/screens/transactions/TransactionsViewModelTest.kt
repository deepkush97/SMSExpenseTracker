package com.smsexpensetracker.ui.screens.transactions

import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import com.smsexpensetracker.domain.usecase.GetTransactionsUseCase
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import com.smsexpensetracker.domain.value.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
    private lateinit var viewModel: TransactionsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getTransactionsUseCase = mockk()
        bankRepository = mockk()
        categoryRepository = mockk()
        transactionRepository = mockk()
        smsSyncUseCase = mockk()
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

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase)
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

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase)
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

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase)
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

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val futureMonth = YearMonth.now().plusMonths(1)
        viewModel.onMonthChange(futureMonth)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.currentMonth != futureMonth)
    }

    @Test
    fun `onCategoryChange calls repository`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        coEvery { transactionRepository.updateTransactionCategory(any(), any()) } returns Unit

        viewModel = TransactionsViewModel(getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase)
        viewModel.onCategoryChange(1L, 5L)
        advanceUntilIdle()

        coVerify { transactionRepository.updateTransactionCategory(1L, 5L) }
    }

    @Test
    fun `sync runs use case and publishes result message`() = runTest(testDispatcher) {
        coEvery { getTransactionsUseCase() } returns MutableStateFlow(emptyList())
        every { bankRepository.getAllBanks() } returns MutableStateFlow(emptyList())
        every { categoryRepository.getAllCategories() } returns MutableStateFlow(emptyList())
        coEvery { smsSyncUseCase.sync() } returns SyncResult(scanned = 5, inserted = 2, unparsed = 1)

        viewModel = TransactionsViewModel(
            getTransactionsUseCase, bankRepository, categoryRepository, transactionRepository, smsSyncUseCase
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
}
