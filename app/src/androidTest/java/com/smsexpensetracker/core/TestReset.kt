package com.smsexpensetracker.core

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.smsexpensetracker.core.database.SmsExpenseDatabase
import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.core.settings.OnboardingPreferences
import com.smsexpensetracker.di.SettingsModule
import com.smsexpensetracker.util.TestPermissions
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description

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

class ResetRule : TestWatcher() {
    override fun starting(description: Description) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppState.reset(context)
        TestPermissions.revoke(context)
    }
}
