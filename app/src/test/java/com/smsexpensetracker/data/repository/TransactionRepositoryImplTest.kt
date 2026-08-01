package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.database.entity.ParseMethod
import com.smsexpensetracker.core.database.entity.TransactionEntity
import com.smsexpensetracker.core.database.entity.TransactionType
import com.smsexpensetracker.domain.model.ParseMethod as DomainParseMethod
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
import java.security.MessageDigest
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

    @Test
    fun `insert maps manual parseMethod into entity`() = runTest {
        coEvery { transactionDao.insert(any<TransactionEntity>()) } returns 5L
        repo.insert(
            Transaction(
                id = 0L, bankId = 1L, amount = 2500L,
                transactionType = com.smsexpensetracker.domain.model.TransactionType.DEBIT,
                description = "Zomato", transactionDate = date, categoryId = null,
                rawSms = "", smsTimestamp = 0L, createdAt = date,
                parseMethod = DomainParseMethod.MANUAL
            )
        )
        coVerify {
            transactionDao.insert(
                match<TransactionEntity> {
                    it.parseMethod == ParseMethod.MANUAL && it.rawSms == "" && it.smsTimestamp == 0L
                }
            )
        }
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `insertBatch hashes non-blank bodies and counts inserted rows`() = runTest {
        val txs = listOf(
            Transaction(
                id = 0L, bankId = 1L, amount = 100L,
                transactionType = com.smsexpensetracker.domain.model.TransactionType.DEBIT,
                description = "a", transactionDate = date, categoryId = null,
                rawSms = "hello", smsTimestamp = 1L, createdAt = date,
                parseMethod = DomainParseMethod.SMS
            ),
            Transaction(
                id = 0L, bankId = 1L, amount = 200L,
                transactionType = com.smsexpensetracker.domain.model.TransactionType.CREDIT,
                description = "b", transactionDate = date, categoryId = null,
                rawSms = "", smsTimestamp = 2L, createdAt = date,
                parseMethod = DomainParseMethod.SMS
            )
        )
        coEvery { transactionDao.insertBatchIgnore(any()) } returns longArrayOf(1L, -1L)

        val count = repo.insertBatch(txs)

        assertEquals(1, count)
        coVerify {
            transactionDao.insertBatchIgnore(
                match<List<TransactionEntity>> { list ->
                    list.size == 2 &&
                        list[0].smsBodyHash == sha256Hex("hello") &&
                        list[1].smsBodyHash == null
                }
            )
        }
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
        assertEquals(expected.parseMethod.name, actual.parseMethod.name)
    }

    private fun assertTransactionList(result: List<Transaction>) {
        assertEquals(2, result.size)
        result.forEachIndexed { index, res ->
            assertTransaction(entities[index], res)
        }
    }
}