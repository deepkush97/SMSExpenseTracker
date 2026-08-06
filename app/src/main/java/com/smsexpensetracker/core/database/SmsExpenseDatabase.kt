package com.smsexpensetracker.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smsexpensetracker.core.database.dao.BankDao
import com.smsexpensetracker.core.database.dao.CategoryDao
import com.smsexpensetracker.core.database.dao.ParseLogDao
import com.smsexpensetracker.core.database.dao.SmsRuleDao
import com.smsexpensetracker.core.database.dao.SyncMetaDao
import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.database.entity.BankEntity
import com.smsexpensetracker.core.database.entity.CategoryEntity
import com.smsexpensetracker.core.database.entity.ParseLogEntity
import com.smsexpensetracker.core.database.entity.SmsRuleEntity
import com.smsexpensetracker.core.database.entity.SyncMetaEntity
import com.smsexpensetracker.core.database.entity.TransactionEntity


@Database(
    entities = [
        BankEntity::class,
        CategoryEntity::class,
        ParseLogEntity::class,
        SmsRuleEntity::class,
        SyncMetaEntity::class,
        TransactionEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SmsExpenseDatabase : RoomDatabase() {
    abstract fun bankDao(): BankDao
    abstract fun smsRuleDao(): SmsRuleDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun parseLogDao(): ParseLogDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        @Volatile
        private var INSTANCE: SmsExpenseDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `transactions_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bankId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`transactionDate` INTEGER NOT NULL, `categoryId` INTEGER, " +
                        "`rawSms` TEXT NOT NULL, `smsTimestamp` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`parseMethod` TEXT NOT NULL DEFAULT 'SMS', " +
                        "FOREIGN KEY(`bankId`) REFERENCES `banks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL(
                    "INSERT INTO `transactions_new` (" +
                        "id, bankId, amount, type, description, transactionDate, categoryId, " +
                        "rawSms, smsTimestamp, createdAt, parseMethod) " +
                        "SELECT id, bankId, amount, type, description, transactionDate, categoryId, " +
                        "rawSms, smsTimestamp, createdAt, 'SMS' FROM `transactions`"
                )
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `smsBodyHash` TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_smsBodyHash` ON `transactions` (`smsBodyHash`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `categories` SET `isDefault` = 1 WHERE `id` BETWEEN 1 AND 14")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sms_rules` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `transaction_labels`")
                db.execSQL("DROP TABLE IF EXISTS `user_category_rules`")
            }
        }

        fun getInstance(context: Context): SmsExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsExpenseDatabase::class.java,
                    "sms_expense_tracker.db"
                ).addCallback(SeedDatabaseCallback())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
//                    .setQueryCallback(Executors.newSingleThreadExecutor()) { sqlQuery, bindArgs ->
//                        // some query logs for debug
//                    }
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

}