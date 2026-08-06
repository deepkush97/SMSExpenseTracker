package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.CategoryDao
import com.smsexpensetracker.core.database.dao.UserCategoryRuleDao
import com.smsexpensetracker.core.database.entity.CategoryEntity
import com.smsexpensetracker.core.database.entity.UserCategoryRuleEntity
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.UserCategoryRule
import com.smsexpensetracker.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val userCategoryRuleDao: UserCategoryRuleDao
) : CategoryRepository {
    override fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories().map { item -> item.map { it.toDomain() } }

    override suspend fun getCategoryById(id: Long): Category? =
        categoryDao.getAllCategoryById(id)?.toDomain()

    override suspend fun insert(category: Category): Long =
        categoryDao.insert(category.toEntity())

    override suspend fun update(category: Category) {
        categoryDao.update(category.toEntity())
    }

    override suspend fun delete(category: Category) {
        categoryDao.delete(category.toEntity())
    }

    private fun CategoryEntity.toDomain() = Category(id, name, icon, color, isDefault)

    override fun getRules(): Flow<List<UserCategoryRule>> =
        userCategoryRuleDao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun insertRule(rule: UserCategoryRule): Long =
        userCategoryRuleDao.insert(
            UserCategoryRuleEntity(id = rule.id, pattern = rule.pattern, categoryId = rule.categoryId)
        )

    override suspend fun deleteRule(rule: UserCategoryRule) {
        userCategoryRuleDao.delete(
            UserCategoryRuleEntity(id = rule.id, pattern = rule.pattern, categoryId = rule.categoryId)
        )
    }

    private fun UserCategoryRuleEntity.toDomain() = UserCategoryRule(id, pattern, categoryId)

    private fun Category.toEntity() = CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isDefault = isDefault
    )
}