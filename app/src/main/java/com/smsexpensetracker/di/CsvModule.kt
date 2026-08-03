package com.smsexpensetracker.di

import android.content.Context
import com.smsexpensetracker.data.csv.CsvExporter
import com.smsexpensetracker.data.csv.CsvImporter
import com.smsexpensetracker.domain.repository.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CsvModule {
    @Provides
    @Singleton
    fun provideCsvExporter(
        @ApplicationContext context: Context,
        transactionRepository: TransactionRepository
    ): CsvExporter = CsvExporter(context, context.filesDir, transactionRepository)

    @Provides
    @Singleton
    fun provideCsvImporter(
        @ApplicationContext context: Context,
        transactionRepository: TransactionRepository
    ): CsvImporter = CsvImporter(context.contentResolver, transactionRepository)
}
