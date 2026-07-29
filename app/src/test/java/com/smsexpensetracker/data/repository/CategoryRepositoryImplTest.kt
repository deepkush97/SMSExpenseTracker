package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.CategoryDao
import com.smsexpensetracker.core.database.entity.CategoryEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CategoryRepositoryImplTest {
    private val categoryDao = mockk<CategoryDao>()
    private lateinit var repo: CategoryRepositoryImpl

    @Before
    fun setup() {
        repo = CategoryRepositoryImpl(categoryDao)
    }

    @Test
    fun `getAllBanks maps entities to domain models`() = runTest {
        val entities = listOf<CategoryEntity>(
            CategoryEntity(1, "Groceries", "shopping_cart", -13956304),
            CategoryEntity(2, "Fuel", "local_gas_station", -48060),
            CategoryEntity(3, "Shopping", "shopping_bag", -10496),
        )

        every { categoryDao.getAllCategories() } returns flowOf(entities)

        val result = repo.getAllCategories().first()

        assertEquals(3, result.size)
        result.forEachIndexed { index, res ->
            assertEquals(entities[index].id, res.id)
            assertEquals(entities[index].name, res.name)
            assertEquals(entities[index].icon, res.icon)
            assertEquals(entities[index].color, res.color)
        }
    }

    @Test
    fun `getAllCategoryById returns mapped category when found`() = runTest {
        val entity = CategoryEntity(1, "Groceries", "shopping_cart", -13956304)
        coEvery { categoryDao.getAllCategoryById(1L) } returns entity

        val result =
            repo.getCategoryById(1L)
        assertEquals(entity.id, result?.id)
        assertEquals(entity.name, result?.name)
        assertEquals(entity.icon, result?.icon)
        assertEquals(entity.color, result?.color)
        coVerify { categoryDao.getAllCategoryById(1L) }
    }

    @Test
    fun `getAllCategoryById returns null when not found`() = runTest {
        coEvery { categoryDao.getAllCategoryById(1L) } returns null

        val result =
            repo.getCategoryById(1L)

        assertEquals(null, result)
        coVerify { categoryDao.getAllCategoryById(1L) }
    }
}