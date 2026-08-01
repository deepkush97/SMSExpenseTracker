package com.smsexpensetracker.di

import com.smsexpensetracker.data.repository.BankRepositoryImpl
import com.smsexpensetracker.data.repository.CategoryRepositoryImpl
import com.smsexpensetracker.data.repository.SmsRuleRepositoryImpl
import com.smsexpensetracker.data.repository.TransactionRepositoryImpl
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        impl: TransactionRepositoryImpl
    ): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindBankRepository(
        impl: BankRepositoryImpl
    ): BankRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        impl: CategoryRepositoryImpl
    ): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindSmsRuleRepository(
        impl: SmsRuleRepositoryImpl
    ): SmsRuleRepository
}
