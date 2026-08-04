package com.smsexpensetracker.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DemoDataBarrierDialog(
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Demo data present") },
        text = {
            Text("Delete demo data before adding real data, so demo and real transactions don't mix.")
        },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) { Text("Delete demo data") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
