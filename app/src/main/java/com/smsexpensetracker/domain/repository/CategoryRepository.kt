package com.smsexpensetracker.domain.repository

import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.UserCategoryRule
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>
    suspend fun getCategoryById(id: Long): Category?

    suspend fun insert(category: Category): Long

    suspend fun update(category: Category)

    suspend fun delete(category: Category)

    fun getRules(): Flow<List<UserCategoryRule>>
    suspend fun insertRule(rule: UserCategoryRule): Long
    suspend fun deleteRule(rule: UserCategoryRule)
}