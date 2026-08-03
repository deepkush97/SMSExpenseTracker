package com.smsexpensetracker

import android.app.Application
import com.smsexpensetracker.data.logging.LoggingSetup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SmsExpenseApp : Application() {

    @Inject
    lateinit var loggingSetup: LoggingSetup

    override fun onCreate() {
        super.onCreate()
        loggingSetup.install()
    }
}
