package com.smsexpensetracker.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
import com.smsexpensetracker.core.database.entity.TransactionLabelEntity
import com.smsexpensetracker.core.database.entity.UserCategoryRuleEntity


@Database(
    entities = [
        BankEntity::class,
        CategoryEntity::class,
        ParseLogEntity::class,
        SmsRuleEntity::class,
        SyncMetaEntity::class,
        TransactionEntity::class,
        TransactionLabelEntity::class,
        UserCategoryRuleEntity::class
    ],
    version = 1,
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

        fun getInstance(context: Context): SmsExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsExpenseDatabase::class.java,
                    "sms_expense_tracker.db"
                ).addCallback(SeedDatabaseCallback())
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