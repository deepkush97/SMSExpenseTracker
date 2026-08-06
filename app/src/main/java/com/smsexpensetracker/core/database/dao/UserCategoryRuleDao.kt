package com.smsexpensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.smsexpensetracker.core.database.entity.UserCategoryRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCategoryRuleDao {
    @Query("SELECT * FROM user_category_rules ORDER BY id")
    fun getAll(): Flow<List<UserCategoryRuleEntity>>

    @Insert
    suspend fun insert(rule: UserCategoryRuleEntity): Long

    @Delete
    suspend fun delete(rule: UserCategoryRuleEntity)

    @Query("DELETE FROM user_category_rules WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)
}
