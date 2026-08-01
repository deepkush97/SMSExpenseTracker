package com.smsexpensetracker.ui.screens.categories

import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.repository.CategoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
class CategoryManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<CategoryRepository>()

    private val food = Category(id = 1, name = "Food", icon = "restaurant", color = -13108, isDefault = true)
    private val coffee = Category(id = 2, name = "Coffee", icon = "local_cafe", color = -10496, isDefault = false)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `categories flow emits repository list`() = runTest(testDispatcher) {
        every { repository.getAllCategories() } returns flowOf(listOf(food, coffee))
        val viewModel = CategoryManagementViewModel(repository)
        val job = launch { viewModel.categories.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(food, coffee), viewModel.categories.value)
        job.cancel()
    }

    @Test
    fun `addCategory inserts with isDefault false`() = runTest(testDispatcher) {
        every { repository.getAllCategories() } returns flowOf(emptyList())
        coEvery { repository.insert(any()) } returns 3L
        val viewModel = CategoryManagementViewModel(repository)
        viewModel.addCategory("Travel", "flight", -13676760)
        advanceUntilIdle()
        coVerify {
            repository.insert(
                Category(id = 0, name = "Travel", icon = "flight", color = -13676760, isDefault = false)
            )
        }
    }

    @Test
    fun `updateCategory updates the category`() = runTest(testDispatcher) {
        every { repository.getAllCategories() } returns flowOf(emptyList())
        coEvery { repository.update(any()) } returns Unit
        val viewModel = CategoryManagementViewModel(repository)
        val updated = coffee.copy(name = "Cafe")
        viewModel.updateCategory(updated)
        advanceUntilIdle()
        coVerify { repository.update(updated) }
    }

    @Test
    fun `deleteCategory deletes non-default category`() = runTest(testDispatcher) {
        every { repository.getAllCategories() } returns flowOf(emptyList())
        coEvery { repository.delete(any()) } returns Unit
        val viewModel = CategoryManagementViewModel(repository)
        viewModel.deleteCategory(coffee)
        advanceUntilIdle()
        coVerify { repository.delete(coffee) }
    }

    @Test
    fun `deleteCategory guards seeded category`() = runTest(testDispatcher) {
        every { repository.getAllCategories() } returns flowOf(emptyList())
        val viewModel = CategoryManagementViewModel(repository)
        viewModel.deleteCategory(food)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.delete(any()) }
    }
}
