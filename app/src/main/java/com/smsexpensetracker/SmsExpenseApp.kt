package com.smsexpensetracker

import android.app.Application
import com.smsexpensetracker.data.demo.DemoDataSeeder
import com.smsexpensetracker.data.logging.LoggingSetup
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SmsExpenseApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var demoDataSeeder: DemoDataSeeder

    @Inject
    lateinit var loggingSetup: LoggingSetup

    override fun onCreate() {
        super.onCreate()
        loggingSetup.install()
        appScope.launch { demoDataSeeder.seedIfEmpty() }
    }
}
