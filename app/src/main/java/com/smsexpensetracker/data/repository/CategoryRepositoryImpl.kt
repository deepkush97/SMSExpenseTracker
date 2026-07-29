package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.CategoryDao
import com.smsexpensetracker.core.database.entity.CategoryEntity
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao
) : CategoryRepository {
    override fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories().map { item -> item.map { it.toDomain() } }

    override suspend fun getCategoryById(id: Long): Category? =
        categoryDao.getAllCategoryById(id)?.toDomain()

    private fun CategoryEntity.toDomain() = Category(id, name, icon, color, isDefault)
}