package com.smsexpensetracker.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.core.database.dao.TransactionLabelDao
import com.smsexpensetracker.core.database.entity.BankEntity
import com.smsexpensetracker.core.database.entity.TransactionEntity
import com.smsexpensetracker.core.database.entity.TransactionLabelEntity
import com.smsexpensetracker.core.database.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class TransactionLabelDaoTest {

    private lateinit var db: SmsExpenseDatabase
    private lateinit var transactionLabelDao: TransactionLabelDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmsExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionLabelDao = db.transactionLabelDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun labelCrudAndCascadeDelete() = runTest {
        val bankId = db.bankDao().insert(BankEntity(name = "HDFC Bank", smsSender = "HDFCBK"))
        val txId = db.transactionDao().insert(
            TransactionEntity(
                bankId = bankId, amount = 1000L, type = TransactionType.DEBIT,
                description = "Test", transactionDate = LocalDateTime.now(), rawSms = "raw",
                smsTimestamp = System.currentTimeMillis()
            )
        )
        db.transactionLabelDao().insert(TransactionLabelEntity(transactionId = txId, label = "Shopping"))

        assertEquals(1, db.transactionLabelDao().getAllForTransaction(txId).first().size)
        assertEquals("Shopping", db.transactionLabelDao().getAllForTransaction(txId).first()[0].label)

        db.transactionLabelDao().deleteForTransaction(txId)
        assertEquals(0, db.transactionLabelDao().getAllForTransaction(txId).first().size)

        db.transactionLabelDao().insert(TransactionLabelEntity(transactionId = txId, label = "Shopping"))
        db.transactionDao().delete(db.transactionDao().getTransactionById(txId)!!)
        assertEquals(0, db.transactionLabelDao().getAllForTransaction(txId).first().size)
    }
}
