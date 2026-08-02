package com.smsexpensetracker.ui.screens.banks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.ui.util.validatePattern
import com.smsexpensetracker.ui.util.validateRuleDescription

@Composable
fun RuleDialog(
    existing: SmsRule?,
    onSave: (description: String, pattern: String) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var pattern by remember { mutableStateOf(existing?.pattern ?: "") }

    val descriptionError = validateRuleDescription(description)
    val patternError = validatePattern(pattern)
    val isValid = descriptionError == null && patternError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add rule" else "Edit rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    isError = descriptionError != null,
                    supportingText = descriptionError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern (regex)") },
                    isError = patternError != null,
                    supportingText = patternError?.let { { Text(it) } },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(description.trim(), pattern.trim()) },
                enabled = isValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
