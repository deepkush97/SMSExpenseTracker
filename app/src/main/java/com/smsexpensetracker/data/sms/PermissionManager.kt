package com.smsexpensetracker.data.sms

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun shouldShowRationale(activity: Activity?): Boolean =
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.READ_SMS
        )

    fun openSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}
