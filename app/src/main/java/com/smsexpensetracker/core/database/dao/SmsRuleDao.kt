package com.smsexpensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

import com.smsexpensetracker.core.database.entity.SmsRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(smsRule: SmsRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(smsRules: List<SmsRuleEntity>)

    @Update
    suspend fun update(smsRule: SmsRuleEntity)

    @Delete
    suspend fun delete(smsRule: SmsRuleEntity)

    @Query("SELECT * FROM sms_rules WHERE bankId = :bankId ORDER BY description ASC")
    fun getRulesForBank(bankId: Long): Flow<List<SmsRuleEntity>>

    @Query("SELECT * FROM sms_rules ORDER BY bankId, description")
    fun getAllRules(): Flow<List<SmsRuleEntity>>

    @Query("SELECT * FROM sms_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): SmsRuleEntity?
}