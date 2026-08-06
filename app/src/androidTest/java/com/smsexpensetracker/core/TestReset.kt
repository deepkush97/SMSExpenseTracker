package com.smsexpensetracker.core

import android.content.Context
import com.smsexpensetracker.core.database.SmsExpenseDatabase
import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.core.settings.OnboardingPreferences
import com.smsexpensetracker.di.SettingsModule
import kotlinx.coroutines.runBlocking

object AppState {
    fun reset(context: Context) {
        val app = context.applicationContext
        runBlocking {
            OnboardingPreferences(SettingsModule.provideSettingsDataStore(app))
                .setOnboardingComplete(false)
            DemoDataPreferences(SettingsModule.provideSettingsDataStore(app))
                .setDemoDataLoaded(false)
            SmsExpenseDatabase.getInstance(app).transactionDao().deleteAll()
        }
    }
}
