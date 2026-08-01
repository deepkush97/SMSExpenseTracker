package com.smsexpensetracker.domain.repository

import com.smsexpensetracker.domain.model.SmsRule
import kotlinx.coroutines.flow.Flow

interface SmsRuleRepository {
    fun getAllRules(): Flow<List<SmsRule>>
    fun getRulesForBank(bankId: Long): Flow<List<SmsRule>>
    suspend fun getRuleById(id: Long): SmsRule?
    suspend fun insert(rule: SmsRule): Long
    suspend fun update(rule: SmsRule)

    suspend fun delete(rule: SmsRule)
}