package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.SmsRuleDao
import com.smsexpensetracker.core.database.entity.SmsRuleEntity
import com.smsexpensetracker.domain.model.SmsRule
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

class SmsRuleRepositoryImplTest {
    private val smsRuleDao = mockk<SmsRuleDao>()
    private lateinit var repo: SmsRuleRepositoryImpl

    @Before
    fun setup() {
        repo = SmsRuleRepositoryImpl(smsRuleDao)
    }

    @Test
    fun `getAllRules maps entities to domain models`() = runTest {
        val entities = listOf<SmsRuleEntity>(
            SmsRuleEntity(1, 1, "%pattern%", "Pattern description"),
            SmsRuleEntity(2, 2, "%pattern2%", "Pattern 2 description"),
        )

        every { smsRuleDao.getAllRules() } returns flowOf(entities)

        val result = repo.getAllRules().first()

        assertEquals(2, result.size)
        result.forEachIndexed { index, res ->
            assertEquals(entities[index].id, res.id)
            assertEquals(entities[index].bankId, res.bankId)
            assertEquals(entities[index].pattern, res.pattern)
            assertEquals(entities[index].description, res.description)
        }
    }

    @Test
    fun `getRuleById returns mapped bank when found`() = runTest {
        val entity = SmsRuleEntity(1, 1, "%pattern%", "Pattern description")

        coEvery { smsRuleDao.getRuleById(1L) } returns entity

        val result =
            repo.getRuleById(1L)
        assertEquals(entity.id, result?.id)
        assertEquals(entity.bankId, result?.bankId)
        assertEquals(entity.pattern, result?.pattern)
        assertEquals(entity.description, result?.description)
        coVerify { smsRuleDao.getRuleById(1L) }
    }

    @Test
    fun `getRuleById returns null when not found`() = runTest {
        coEvery { smsRuleDao.getRuleById(1L) } returns null

        val result =
            repo.getRuleById(1L)
        assertEquals(null, result)
        coVerify { smsRuleDao.getRuleById(1L) }
    }

    @Test
    fun `getRulesForBank returns mapped bank when found`() = runTest {
        val entities = listOf<SmsRuleEntity>(
            SmsRuleEntity(1, 1, "%pattern%", "Pattern description"),
            SmsRuleEntity(2, 2, "%pattern2%", "Pattern 2 description"),
        )
        coEvery { smsRuleDao.getRulesForBank(1L) } returns flowOf(entities)
        val results =
            repo.getRulesForBank(1L).first()

        assertEquals(2, results.size)
        results.forEachIndexed { index, res ->
            assertEquals(entities[index].id, res.id)
            assertEquals(entities[index].bankId, res.bankId)
            assertEquals(entities[index].pattern, res.pattern)
            assertEquals(entities[index].description, res.description)
        }
    }

    @Test
    fun `getRulesForBank returns null when not found`() = runTest {
        coEvery { smsRuleDao.getRulesForBank(1L) } returns flowOf(emptyList())

        val result =
            repo.getRulesForBank(1L).first()
        assertEquals(emptyList<SmsRule>(), result)
    }

    @Test
    fun `insert returns id after insert`() = runTest {
        val entity = SmsRuleEntity(
            1,
            1,
            "%pattern%",
            "Pattern description"
        )
        coEvery {
            smsRuleDao.insert(
                entity
            )
        } returns 1L

        val result =
            repo.insert(SmsRule(entity.id, entity.bankId, entity.pattern, entity.description))

        assertEquals(1L, result)
    }
}