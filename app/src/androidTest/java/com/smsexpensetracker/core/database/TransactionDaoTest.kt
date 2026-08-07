package com.smsexpensetracker.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.database.entity.BankEntity
import com.smsexpensetracker.core.database.entity.CategoryEntity
import com.smsexpensetracker.core.database.entity.ParseMethod
import com.smsexpensetracker.core.database.entity.TransactionEntity
import com.smsexpensetracker.core.database.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var db: SmsExpenseDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmsExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = db.transactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedBankAndCategory(): Pair<Long, Long> {
        val bankId = db.bankDao().insert(BankEntity(name = "HDFC Bank", smsSender = "HDFCBK"))
        val categoryId = db.categoryDao().insert(CategoryEntity(name = "Shopping", icon = "", color = 0))
        return bankId to categoryId
    }

    private fun tx(bankId: Long, categoryId: Long? = null, rawSms: String = "sms body", hash: String? = null) =
        TransactionEntity(
            bankId = bankId,
            amount = 1000L,
            type = TransactionType.DEBIT,
            description = "Test",
            transactionDate = LocalDateTime.now(),
            categoryId = categoryId,
            rawSms = rawSms,
            smsTimestamp = System.currentTimeMillis(),
            parseMethod = ParseMethod.SMS,
            smsBodyHash = hash
        )

    @Test
    fun insertQueryUpdateDeleteRoundTrip() = runTest {
        val (bankId, categoryId) = seedBankAndCategory()
        val id = transactionDao.insert(tx(bankId, categoryId))

        val found = transactionDao.getTransactionById(id)
        assertNotNull(found)
        assertEquals("Test", found!!.description)
        assertEquals(categoryId, found.categoryId)

        transactionDao.update(found.copy(description = "Updated"))
        assertEquals("Updated", transactionDao.getTransactionById(id)!!.description)

        transactionDao.delete(transactionDao.getTransactionById(id)!!)
        assertNull(transactionDao.getTransactionById(id))
    }

    @Test
    fun insertBatchIgnoreDeduplicatesBySmsBodyHash() = runTest {
        val (bankId, _) = seedBankAndCategory()
        val original = tx(bankId, rawSms = "same raw body", hash = "abc123")
        val dup = original.copy(id = 0L, smsBodyHash = "abc123")

        val ids = transactionDao.insertBatchIgnore(listOf(original, dup))

        assertEquals(2, ids.size)
        assertEquals(1, transactionDao.getAllTransactions().first().size)
        assertEquals(1, ids.count { it > 0 })
    }

    @Test
    fun updatePreservesSmsBodyHash() = runTest {
        val (bankId, _) = seedBankAndCategory()
        val id = transactionDao.insert(tx(bankId, rawSms = "body", hash = "keepme"))

        transactionDao.updateTransactionFields(
            id = id, bankId = bankId, amount = 9999L, type = TransactionType.CREDIT,
            description = "edited", transactionDate = LocalDateTime.now(), categoryId = null
        )

        assertEquals("keepme", transactionDao.getTransactionById(id)!!.smsBodyHash)
        assertEquals(9999L, transactionDao.getTransactionById(id)!!.amount)
    }

    @Test
    fun insertBatchIgnoreReturnsMinusOneForIgnoredDuplicates() = runTest {
        val (bankId, _) = seedBankAndCategory()
        val original = tx(bankId, rawSms = "raw", hash = "hash1")
        transactionDao.insertBatchIgnore(listOf(original))

        val second = transactionDao.insertBatchIgnore(listOf(original.copy(id = 0L)))

        assertEquals(1, second.size)
        assertEquals(-1L, second[0])
    }
}
