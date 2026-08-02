package com.smsexpensetracker.ui.screens.banks

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.smsexpensetracker.domain.model.Bank

@Composable
fun BankDeleteDialog(
    bank: Bank,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${bank.name}?") },
        text = { Text("This bank and its SMS rules will be removed.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
