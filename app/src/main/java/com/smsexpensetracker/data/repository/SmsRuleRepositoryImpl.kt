package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.SmsRuleDao
import com.smsexpensetracker.core.database.entity.SmsRuleEntity
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SmsRuleRepositoryImpl @Inject constructor(
    private val smsRuleDao: SmsRuleDao
) : SmsRuleRepository {
    override fun getAllRules(): Flow<List<SmsRule>> =
        smsRuleDao.getAllRules().map { list -> list.map { it.toDomain() } }

    override fun getRulesForBank(bankId: Long): Flow<List<SmsRule>> =
        smsRuleDao.getRulesForBank(bankId).map { list -> list.map { it.toDomain() } }

    override suspend fun getRuleById(id: Long): SmsRule? =
        smsRuleDao.getRuleById(id)?.toDomain()

    override suspend fun insert(rule: SmsRule): Long =
        smsRuleDao.insert(rule.toEntity())

    override suspend fun update(rule: SmsRule) {
        smsRuleDao.update(rule.toEntity())
    }

    override suspend fun delete(rule: SmsRule) {
        smsRuleDao.delete(rule.toEntity())
    }

    private fun SmsRuleEntity.toDomain() = SmsRule(id, bankId, pattern, description, isActive)

    private fun SmsRule.toEntity() = SmsRuleEntity(id, bankId, pattern, description, isActive)
}