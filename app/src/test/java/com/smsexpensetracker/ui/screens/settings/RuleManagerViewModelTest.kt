package com.smsexpensetracker.ui.screens.settings

import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.UserCategoryRule
import com.smsexpensetracker.domain.repository.CategoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleManagerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val categoryRepository = mockk<CategoryRepository>()

    private val shopping = Category(10L, "Shopping", "", 0, false)
    private val food = Category(11L, "Food", "", 0, false)
    private val rule1 = UserCategoryRule(id = 1L, pattern = "amazon", categoryId = 10L)
    private val rule2 = UserCategoryRule(id = 2L, pattern = "swiggy", categoryId = 11L)
    private val rule3 = UserCategoryRule(id = 3L, pattern = "occupied", categoryId = 99L)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `rules are resolved to category names on init`() = runTest(dispatcher) {
        coEvery { categoryRepository.getRules() } returns flowOf(listOf(rule1, rule2, rule3))
        coEvery { categoryRepository.getAllCategories() } returns flowOf(listOf(shopping, food))

        val vm = RuleManagerViewModel(categoryRepository)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.rules.size)
        assertEquals("Shopping", vm.uiState.value.rules[0].categoryName)
        assertEquals("Food", vm.uiState.value.rules[1].categoryName)
        assertEquals("Unknown", vm.uiState.value.rules[2].categoryName)
        job.cancel()
    }

    @Test
    fun `empty rules produce empty state`() = runTest(dispatcher) {
        coEvery { categoryRepository.getRules() } returns flowOf(emptyList())
        coEvery { categoryRepository.getAllCategories() } returns flowOf(listOf(shopping))

        val vm = RuleManagerViewModel(categoryRepository)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.rules.size)
    }

    @Test
    fun `delete calls deleteRule on repository`() = runTest(dispatcher) {
        coEvery { categoryRepository.getRules() } returns flowOf(listOf(rule1))
        coEvery { categoryRepository.getAllCategories() } returns flowOf(listOf(shopping))
        coEvery { categoryRepository.deleteRule(any()) } returns Unit

        val vm = RuleManagerViewModel(categoryRepository)
        advanceUntilIdle()
        vm.delete(rule1)
        advanceUntilIdle()

        coVerify { categoryRepository.deleteRule(rule1) }
    }
}