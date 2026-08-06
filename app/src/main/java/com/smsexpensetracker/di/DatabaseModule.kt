package com.smsexpensetracker.di

import android.content.Context
import com.smsexpensetracker.core.database.SmsExpenseDatabase
import com.smsexpensetracker.core.database.dao.BankDao
import com.smsexpensetracker.core.database.dao.CategoryDao
import com.smsexpensetracker.core.database.dao.ParseLogDao
import com.smsexpensetracker.core.database.dao.SmsRuleDao
import com.smsexpensetracker.core.database.dao.SyncMetaDao
import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.database.dao.TransactionLabelDao
import com.smsexpensetracker.core.database.dao.UserCategoryRuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SmsExpenseDatabase =
        SmsExpenseDatabase.getInstance(context)

    @Provides
    fun provideBankDao(db: SmsExpenseDatabase): BankDao = db.bankDao()

    @Provides
    fun provideSmsRuleDao(db: SmsExpenseDatabase): SmsRuleDao = db.smsRuleDao()

    @Provides
    fun provideTransactionDao(db: SmsExpenseDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: SmsExpenseDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideParseLogDao(db: SmsExpenseDatabase): ParseLogDao = db.parseLogDao()

    @Provides
    fun provideSyncMetaDao(db: SmsExpenseDatabase): SyncMetaDao = db.syncMetaDao()

    @Provides
    fun provideUserCategoryRuleDao(db: SmsExpenseDatabase): UserCategoryRuleDao = db.userCategoryRuleDao()

    @Provides
    fun provideTransactionLabelDao(db: SmsExpenseDatabase): TransactionLabelDao = db.transactionLabelDao()
}