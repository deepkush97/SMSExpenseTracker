package com.smsexpensetracker.di

import android.content.Context
import com.smsexpensetracker.data.sms.SmsReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SmsModule {
    @Provides
    @Singleton
    fun provideSmsReader(@ApplicationContext context: Context): SmsReader =
        SmsReader(context.contentResolver)
}
