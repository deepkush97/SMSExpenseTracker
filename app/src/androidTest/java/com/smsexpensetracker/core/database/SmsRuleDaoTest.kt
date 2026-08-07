package com.smsexpensetracker.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.core.database.dao.SmsRuleDao
import com.smsexpensetracker.core.database.entity.BankEntity
import com.smsexpensetracker.core.database.entity.SmsRuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsRuleDaoTest {

    private lateinit var db: SmsExpenseDatabase
    private lateinit var smsRuleDao: SmsRuleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmsExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        smsRuleDao = db.smsRuleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun ruleCrudRoundTrip() = runTest {
        val bankId = db.bankDao().insert(BankEntity(name = "HDFC Bank", smsSender = "HDFCBK"))
        val rule = SmsRuleEntity(bankId = bankId, pattern = "Spent Rs\\.(.*)", description = "HDFC Debit")
        val id = db.smsRuleDao().insert(rule)

        assertEquals(1, db.smsRuleDao().getAllRules().first().size)
        assertEquals("HDFC Debit", db.smsRuleDao().getRuleById(id)!!.description)

        db.smsRuleDao().update(rule.copy(id = id, isActive = false))
        assertEquals(false, db.smsRuleDao().getRuleById(id)!!.isActive)

        db.smsRuleDao().delete(db.smsRuleDao().getRuleById(id)!!)
        assertEquals(0, db.smsRuleDao().getAllRules().first().size)
    }
}
