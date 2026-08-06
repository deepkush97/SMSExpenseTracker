package com.smsexpensetracker.util

import android.content.Context
import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry

object TestPermissions {
    private val SMS = listOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )

    fun grant(context: Context) {
        val uia = InstrumentationRegistry.getInstrumentation().uiAutomation
        SMS.forEach { perm -> uia.grantRuntimePermission(context.packageName, perm) }
    }
}
