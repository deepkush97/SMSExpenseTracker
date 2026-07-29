package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.BankDao
import com.smsexpensetracker.core.database.entity.BankEntity
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

class BankRepositoryImplTest {
    private val bankDao = mockk<BankDao>()
    private lateinit var repo: BankRepositoryImpl

    @Before
    fun setup() {
        repo = BankRepositoryImpl(bankDao)
    }

    @Test
    fun `getAllBanks maps entities to domain models`() = runTest {
        val entities = listOf<BankEntity>(
            BankEntity(1, "HDFC", "HDFCBK"),
            BankEntity(2, "ICICI", "ICICIB"),
            BankEntity(3, "DCB", "DCBANK"),
        )

        every { bankDao.getAllBanks() } returns flowOf(entities)

        val result = repo.getAllBanks().first()

        assertEquals(3, result.size)
        result.forEachIndexed { index, res ->
            assertEquals(entities[index].id, res.id)
            assertEquals(entities[index].name, res.name)
            assertEquals(entities[index].smsSender, res.smsSender)

        }
    }

    @Test
    fun `getBankById returns mapped bank when found`() = runTest {
        val entity = BankEntity(1, "HDFC", "HDFCBK")
        coEvery { bankDao.getBankById(1L) } returns entity

        val result =
            repo.getBankById(1L)
        assertEquals(entity.id, result?.id)
        assertEquals(entity.name, result?.name)
        assertEquals(entity.smsSender, result?.smsSender)
        coVerify { bankDao.getBankById(1L) }
    }

    @Test
    fun `getBankById returns null when not found`() = runTest {
        coEvery { bankDao.getBankById(1L) } returns null

        val result =
            repo.getBankById(1L)
        assertEquals(null, result)
        coVerify { bankDao.getBankById(1L) }
    }

    @Test
    fun `getBankBySender returns mapped bank when found`() = runTest {
        val entity = BankEntity(1, "HDFC", "HDFCBK")
        coEvery { bankDao.getBankBySmsSender("HDFCBK") } returns entity

        val result =
            repo.getBankBySender("HDFCBK")
        assertEquals(entity.id, result?.id)
        assertEquals(entity.name, result?.name)
        assertEquals(entity.smsSender, result?.smsSender)
        coVerify { bankDao.getBankBySmsSender("HDFCBK") }
    }

    @Test
    fun `getBankBySender returns null when not found`() = runTest {
        coEvery { bankDao.getBankBySmsSender("HDFCBK") } returns null

        val result =
            repo.getBankBySender("HDFCBK")
        assertEquals(null, result)
        coVerify { bankDao.getBankBySmsSender("HDFCBK") }
    }
}