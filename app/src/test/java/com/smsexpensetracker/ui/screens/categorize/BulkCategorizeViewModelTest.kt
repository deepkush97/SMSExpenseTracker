package com.smsexpensetracker.ui.screens.categorize

import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.model.UserCategoryRule
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
            tx(3, "PAYMENT VIA AMAZON", 9L)
        )
        coEvery { transactionRepository.getAllTransactions() } returns flowOf(txs)
        coEvery { categoryRepository.getAllCategories() } returns flowOf(emptyList())
        coEvery { transactionRepository.updateTransactionCategory(any(), any()) } returns Unit
        coEvery { categoryRepository.insertRule(any()) } answers {
            rulesFlow.value = rulesFlow.value + UserCategoryRule(0L, "amazon", 9L)
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