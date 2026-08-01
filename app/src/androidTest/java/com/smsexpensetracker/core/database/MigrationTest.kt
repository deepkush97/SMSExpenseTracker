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

    @Test
    fun migrate2To3_addsSmsBodyHashColumn_andUniqueIndex() {
        helper.createDatabase("migration-test-v3", 2).use { db ->
            db.execSQL("INSERT INTO banks (id, name, smsSender) VALUES (1, 'HDFC Bank', 'HDFCBK')")
            db.execSQL(
                "INSERT INTO transactions (bankId, amount, type, description, transactionDate, categoryId, rawSms, smsTimestamp, createdAt, parseMethod) " +
                    "VALUES (1, 1000, 'DEBIT', 'desc', 1750000000, NULL, 'raw', 1750000000, 1750000000, 'MANUAL')"
            )
        }

        val db = helper.runMigrationsAndValidate("migration-test-v3", 3, true, SmsExpenseDatabase.MIGRATION_2_3)

        db.query("SELECT COUNT(*) FROM transactions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }
        db.query("PRAGMA index_list('transactions')").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == "index_transactions_smsBodyHash") found = true
            }
            assertTrue(found)
        }
        db.close()
    }

    @Test
    fun migrate3To4_marksSeededCategoriesAsDefault() {
        helper.createDatabase("migration-test-v4", 3).use { db ->
            db.execSQL("INSERT INTO categories (id, name, icon, color, isDefault) VALUES (1, 'Food', '', 0, 0)")
            db.execSQL("INSERT INTO categories (id, name, icon, color, isDefault) VALUES (15, 'Mine', '', 0, 0)")
        }

        val db = helper.runMigrationsAndValidate("migration-test-v4", 4, true, SmsExpenseDatabase.MIGRATION_3_4)

        db.query("SELECT isDefault FROM categories WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }
        db.query("SELECT isDefault FROM categories WHERE id = 15").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
        db.close()
    }

    @Test
    fun migrate4To5_addsIsActiveColumn() {
        helper.createDatabase("migration-test-v5", 4).use { db ->
            db.execSQL("INSERT INTO banks (id, name, smsSender) VALUES (1, 'HDFC Bank', 'HDFCBK')")
            db.execSQL(
                "INSERT INTO sms_rules (id, bankId, pattern, description) " +
                    "VALUES (1, 1, 'Spent Rs\\\\.([\\\\d,.]+) On HDFC Bank Card', 'HDFC CC Debit')"
            )
        }

        val db = helper.runMigrationsAndValidate("migration-test-v5", 5, true, SmsExpenseDatabase.MIGRATION_4_5)

        db.query("SELECT isActive FROM sms_rules WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }
        db.close()
    }
}
