package com.smsexpensetracker.ui.screens.banks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.ui.util.formatPaisa
import com.smsexpensetracker.ui.util.validatePattern
import com.smsexpensetracker.ui.util.validateRuleDescription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RuleEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val bank by viewModel.bank.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var examplesExpanded by remember { mutableStateOf(false) }

    val descriptionError = validateRuleDescription(state.description)
    val patternError = validatePattern(state.draftPattern)
    val hasMatch = state.testResult != null
    val canSave = descriptionError == null && patternError == null && hasMatch

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    LaunchedEffect(state.saveError) {
        val error = state.saveError
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.consumeSaveError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Rule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::onSave, enabled = canSave) {
                        Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (bank != null) {
                Text(
                    text = bank!!.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = state.sampleSms,
                onValueChange = viewModel::onSampleSmsChange,
                label = { Text("Sample SMS") },
                supportingText = { Text("Paste a real bank SMS to test your pattern") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(28.dp),
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )

            OutlinedTextField(
                value = state.draftPattern,
                onValueChange = viewModel::onPatternChange,
                label = { Text("Pattern (regex)") },
                isError = patternError != null && state.draftPattern.isNotEmpty(),
                supportingText = {
                    Column {
                        if (patternError != null) {
                            Text(patternError)
                        }
                        Text("Group 1 = amount, Group 2 = description")
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(28.dp),
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(onClick = { examplesExpanded = !examplesExpanded }) {
                Text(if (examplesExpanded) "How it works & examples (hide)" else "How it works & examples")
            }
            AnimatedVisibility(visible = examplesExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "The pattern is a regular expression. Group 1 must capture the amount " +
                            "(e.g. 1250.50) and group 2 the description. Test against a real SMS " +
                            "before saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "UPI debit:\nICICI Bank Acct \\w+ debited for Rs ([\\d,.]+) on [\\d-]+; (.+?) credited\\. UPI",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Card spend:\nSpent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "UPI credit:\nAcct \\w+ is credited with Rs ([\\d,.]+) on [\\d-]+ from (.+?)\\. UPI",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description") },
                isError = descriptionError != null && state.description.isNotEmpty(),
                supportingText = descriptionError?.let { { Text(it) } },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = viewModel::onTest,
                enabled = state.sampleSms.isNotBlank() && patternError == null,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test")
            }

            if (state.hasTested) {
                if (hasMatch) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Matches", style = MaterialTheme.typography.titleSmall)
                            RuleEditorResultField("Amount", formatPaisa(state.testResult!!.amount))
                            RuleEditorResultField("Description", state.testResult!!.description)
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No match for this SMS",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleEditorResultField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
