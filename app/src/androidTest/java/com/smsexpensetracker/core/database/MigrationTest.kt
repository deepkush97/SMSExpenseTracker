package com.smsexpensetracker.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SmsExpenseDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesBankData_andAddsParseMethod() {
        helper.createDatabase("migration-test", 1).use { db ->
            db.execSQL("INSERT INTO banks (id, name, smsSender) VALUES (1, 'HDFC Bank', 'HDFCBK')")
            db.execSQL(
                "INSERT INTO transactions (bankId, amount, type, description, transactionDate, categoryId, rawSms, smsTimestamp, createdAt) " +
                    "VALUES (1, 1000, 'DEBIT', 'test desc', 1750000000, NULL, 'raw sms', 1750000000, 1750000000)"
            )
        }

        val db = helper.runMigrationsAndValidate("migration-test", 2, true, SmsExpenseDatabase.MIGRATION_1_2)

        db.execSQL("PRAGMA foreign_keys = ON")

        db.query("SELECT name FROM banks WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("HDFC Bank", cursor.getString(0))
        }
        db.query("SELECT parseMethod FROM transactions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("SMS", cursor.getString(0))
        }
        db.execSQL("INSERT INTO categories (id, name, icon, color, isDefault) VALUES (6, 'Healthcare', '', 0, 0)")
        db.execSQL(
            "INSERT INTO transactions (bankId, amount, type, description, transactionDate, categoryId, rawSms, smsTimestamp, createdAt, parseMethod) " +
                "VALUES (1, 500, 'DEBIT', 'hospital', 1750000000, 6, '', 1750000000, 1750000000, 'MANUAL')"
        )
        db.query("SELECT COUNT(*) FROM transactions WHERE categoryId = 6").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }
    }
}
