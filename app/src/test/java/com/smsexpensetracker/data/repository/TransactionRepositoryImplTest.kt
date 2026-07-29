package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.database.entity.TransactionEntity
import com.smsexpensetracker.core.database.entity.TransactionType
import com.smsexpensetracker.domain.model.Transaction
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
import java.time.LocalDateTime
import java.time.ZoneOffset

class TransactionRepositoryImplTest {
    private val transactionDao = mockk<TransactionDao>()
    private lateinit var repo: TransactionRepositoryImpl
    private val date = LocalDateTime.of(2026, 6, 15, 11, 0, 0)
    private val entities = listOf<TransactionEntity>(
        TransactionEntity(
            1L,
            1L,
            1000,
            TransactionType.DEBIT,
            "debit desc",
            date,
            1L,
            "debit desc raw sms",
            date.toInstant(
                ZoneOffset.UTC
            ).toEpochMilli(),
            date
        ),
        TransactionEntity(
            2L,
            2L,
            2000,
            TransactionType.CREDIT,
            "credit desc",
            date,
            1L,
            "credit desc raw sms",
            date.toInstant(
                ZoneOffset.UTC
            ).toEpochMilli(),
            date
        )
    )

    @Before
    fun setup() {
        repo = TransactionRepositoryImpl(transactionDao)
    }

    @Test
    fun `getAllTransactions maps entities to domain models`() = runTest {
        every { transactionDao.getAllTransactions() } returns flowOf(entities)
        val result = repo.getAllTransactions().first()

        assertTransactionList(result)
    }

    @Test
    fun `getTransactionsByBank maps entities to domain models`() = runTest {
        every { transactionDao.getTransactionsByBank(1L) } returns flowOf(entities)
        val result = repo.getTransactionsByBank(1L).first()

        assertTransactionList(result)
    }

    @Test
    fun `getTransactionsByCategory maps entities to domain models`() = runTest {
        every { transactionDao.getTransactionsByCategory(1L) } returns flowOf(entities)
        val result = repo.getTransactionsByCategory(1L).first()

        assertTransactionList(result)
    }

    @Test
    fun `searchTransactions maps entities to domain models`() = runTest {
        every { transactionDao.searchTransactions("some") } returns flowOf(entities)
        val result = repo.searchTransactions("some").first()

        assertTransactionList(result)
    }

    @Test
    fun `getTransactionsBetweenDates maps entities to domain models`() = runTest {
        every { transactionDao.getTransactionsBetweenDates(1L, 2L) } returns flowOf(entities)
        val result = repo.getTransactionsBetweenDates(1L, 2L).first()

        assertTransactionList(result)
    }


    @Test
    fun `getTransactionById returns mapped bank when found`() = runTest {

        coEvery { transactionDao.getTransactionById(1L) } returns entities[0]

        val result =
            repo.getTransactionById(1L)
        if (result != null) {
            assertTransaction(entities[0], result)
        }
        coVerify { transactionDao.getTransactionById(1L) }
    }

    @Test
    fun `getTransactionById returns null when not found`() = runTest {
        coEvery { transactionDao.getTransactionById(1L) } returns null

        val result =
            repo.getTransactionById(1L)
        assertEquals(null, result)
        coVerify { transactionDao.getTransactionById(1L) }
    }


    @Test
    fun `insert returns id after insert`() = runTest {
        val entity = entities[0]
        coEvery {
            transactionDao.insert(
                entity
            )
        } returns 1L

        val result =
            repo.insert(
                Transaction(
                    entity.id,
                    entity.bankId,
                    entity.amount,
                    com.smsexpensetracker.domain.model.TransactionType.valueOf(entity.type.name),
                    entity.description,
                    entity.transactionDate,
                    entity.categoryId,
                    entity.rawSms,
                    entity.smsTimestamp,
                    entity.createdAt
                )
            )

        assertEquals(1L, result)
    }

    @Test
    fun `delete returns id after delete`() = runTest {
        val entity = entities[0]
        coEvery { transactionDao.delete(any<TransactionEntity>()) } coAnswers { Unit }

        val result =
            repo.delete(
                Transaction(
                    entity.id,
                    entity.bankId,
                    entity.amount,
                    com.smsexpensetracker.domain.model.TransactionType.valueOf(entity.type.name),
                    entity.description,
                    entity.transactionDate,
                    entity.categoryId,
                    entity.rawSms,
                    entity.smsTimestamp,
                    entity.createdAt
                )
            )

        coVerify { transactionDao.delete(any()) }
    }

    private fun assertTransaction(expected: TransactionEntity, actual: Transaction) {
        assertEquals(expected.id, actual.id)
        assertEquals(expected.bankId, actual.bankId)
        assertEquals(expected.amount, actual.amount)
        assertEquals(expected.type.name, actual.transactionType.name)
        assertEquals(expected.description, actual.description)
        assertEquals(expected.transactionDate, actual.transactionDate)
        assertEquals(expected.categoryId, actual.categoryId)
        assertEquals(expected.rawSms, actual.rawSms)
        assertEquals(expected.smsTimestamp, actual.smsTimestamp)
        assertEquals(expected.createdAt, actual.createdAt)
    }

    private fun assertTransactionList(result: List<Transaction>) {
        assertEquals(2, result.size)
        result.forEachIndexed { index, res ->
            assertTransaction(entities[index], res)
        }
    }
}