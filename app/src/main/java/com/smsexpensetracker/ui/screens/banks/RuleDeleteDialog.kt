package com.smsexpensetracker.ui.screens.banks

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.smsexpensetracker.domain.model.SmsRule

@Composable
fun RuleDeleteDialog(
    rule: SmsRule,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${rule.description}?") },
        text = { Text("This SMS rule will no longer be used to parse transactions.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
