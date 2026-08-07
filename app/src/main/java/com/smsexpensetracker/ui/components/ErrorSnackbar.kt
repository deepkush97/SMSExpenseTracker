package com.smsexpensetracker.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ErrorSnackbar(
    message: String,
    snackbarHostState: SnackbarHostState,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    LaunchedEffect(message) {
        if (message.isEmpty()) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) onAction()
    }
}
