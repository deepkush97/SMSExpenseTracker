package com.smsexpensetracker.di

import android.content.Context
import com.smsexpensetracker.data.logging.FileLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
    @Provides
    @Singleton
    fun provideFileLogger(@ApplicationContext context: Context): FileLogger =
        FileLogger(context, context.filesDir)
}
