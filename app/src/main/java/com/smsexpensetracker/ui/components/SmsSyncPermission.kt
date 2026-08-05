package com.smsexpensetracker.ui.components

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.smsexpensetracker.data.sms.PermissionManager

@Composable
fun rememberSmsSyncPermission(
    onGranted: () -> Unit,
    onDenied: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager() }
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants[Manifest.permission.READ_SMS] == true &&
            grants[Manifest.permission.RECEIVE_SMS] == true
        if (allGranted) onGranted() else onDenied()
    }

    val requestSync: () -> Unit = {
        when {
            permissionManager.hasPermission(context) -> onGranted()
            permissionManager.shouldShowRationale(context as? Activity) -> showRationale = true
            else -> launcher.launch(
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
            )
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Allow SMS access?") },
            text = {
                Text("SMS Expense Tracker reads your bank SMS to extract transaction details. SMS data stays on your device.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(
                        arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                    )
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now") }
            }
        )
    }

    return requestSync
}
