package com.smsexpensetracker.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.core.database.dao.BankDao
import com.smsexpensetracker.core.database.entity.BankEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankDaoTest {

    private lateinit var db: SmsExpenseDatabase
    private lateinit var bankDao: BankDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmsExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bankDao = db.bankDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun bankCrudRoundTrip() = runTest {
        val id = db.bankDao().insert(BankEntity(name = "ICICI Bank", smsSender = "ICICIBK"))
        assertEquals(1, db.bankDao().getAllBanks().first().size)
        assertEquals("ICICI Bank", db.bankDao().getBankById(id)!!.name)

        db.bankDao().update(BankEntity(id = id, name = "ICICI", smsSender = "ICICIBK"))
        assertEquals("ICICI", db.bankDao().getBankById(id)!!.name)

        db.bankDao().delete(db.bankDao().getBankById(id)!!)
        assertEquals(0, db.bankDao().getAllBanks().first().size)
    }

    @Test
    fun bankLookupBySmsSender() = runTest {
        db.bankDao().insert(BankEntity(name = "HDFC Bank", smsSender = "HDFCBK"))
        assertEquals("HDFC Bank", db.bankDao().getBankBySmsSender("HDFCBK")!!.name)
    }
}
