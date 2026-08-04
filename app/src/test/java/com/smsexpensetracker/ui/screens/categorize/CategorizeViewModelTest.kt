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
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val travel = Category(id = 4, name = "Travel", icon = "", color = 0xFF00FF00.toInt(), isDefault = false)
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

    @Test
    fun `categories update live when category list changes`() = runTest(testDispatcher) {
        every { transactionRepository.getAllTransactions() } returns flowOf(listOf(tx(1)))
        val categoriesFlow = MutableStateFlow(listOf(food))
        every { categoryRepository.getAllCategories() } returns categoriesFlow
        every { bankRepository.getAllBanks() } returns flowOf(listOf(hdfc))

        val vm = CategorizeViewModel(transactionRepository, categoryRepository, bankRepository)
        advanceUntilIdle()
        assertEquals(listOf(food), vm.uiState.value.categories)

        categoriesFlow.value = listOf(food, travel)
        advanceUntilIdle()

        assertEquals(listOf(food, travel), vm.uiState.value.categories)
    }
}
