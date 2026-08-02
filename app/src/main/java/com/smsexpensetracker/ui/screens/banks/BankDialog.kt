package com.smsexpensetracker.ui.screens.banks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.ui.util.validateBankName
import com.smsexpensetracker.ui.util.validateBankSender

@Composable
fun BankDialog(
    existing: Bank?,
    allBanks: List<Bank>,
    onSave: (name: String, smsSender: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var sender by remember { mutableStateOf(existing?.smsSender ?: "") }

    val nameError = validateBankName(name, allBanks, existing?.id)
    val senderError = validateBankSender(sender)
    val isValid = nameError == null && senderError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add bank" else "Edit bank") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("Sender") },
                    isError = senderError != null,
                    supportingText = senderError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), sender.trim().uppercase()) },
                enabled = isValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
