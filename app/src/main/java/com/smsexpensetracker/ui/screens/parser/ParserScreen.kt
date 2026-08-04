package com.smsexpensetracker.ui.screens.parser

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.ui.components.DemoDataBarrierDialog
import com.smsexpensetracker.ui.theme.Amber40
import com.smsexpensetracker.ui.theme.Amber80
import com.smsexpensetracker.ui.theme.Green40
import com.smsexpensetracker.ui.theme.Green80
import com.smsexpensetracker.ui.theme.Red40
import com.smsexpensetracker.ui.theme.Red80
import com.smsexpensetracker.ui.util.formatPaisa

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserScreen(
    modifier: Modifier = Modifier,
    viewModel: ParserViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var bankExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.showSavedSnackbar) {
        if (state.showSavedSnackbar) {
            snackbarHostState.showSnackbar("Transaction added")
            viewModel.consumeSavedSnackbar()
        }
    }

    LaunchedEffect(state.saveError) {
        val error = state.saveError
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.consumeSaveError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Parser Test",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Paste a real bank SMS to test the parser",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = state.smsInput,
                onValueChange = viewModel::onSmsChange,
                label = { Text("SMS body") },
                shape = RoundedCornerShape(28.dp),
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )

            OutlinedTextField(
                value = state.senderInput,
                onValueChange = viewModel::onSenderChange,
                label = { Text("Sender ID") },
                supportingText = { Text("e.g. AD-HDFCBK-S") },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            BankDropdown(
                banks = state.banks,
                selectedBankId = state.selectedBankId,
                onBankChange = viewModel::onBankSelect,
                modifier = Modifier.fillMaxWidth()
            )

            val autoDetected = remember(state.senderInput, state.selectedBankId, state.banks) {
                if (state.selectedBankId == null) {
                    state.banks.find { it.id == viewModel.detectBank(state.senderInput, state.banks) }?.name
                } else {
                    null
                }
            }
            if (autoDetected != null) {
                Text(
                    text = "Auto-detected: $autoDetected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = viewModel::parse,
                enabled = state.smsInput.isNotBlank() && !state.isParsing,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isParsing) "Parsing..." else "Test Parse")
            }

            val result = state.result
            if (result != null) {
                if (result.errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = result.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    ResultCard(
                        result = result,
                        bankName = state.banks.find { it.id == result.bankId }?.name ?: "Unknown",
                        onAddAsTransaction = viewModel::addAsTransaction,
                        isSaving = state.isSaving
                    )
                }
            }

            if (state.displayRules.isNotEmpty()) {
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
                        Text(
                            text = "Rules for this bank (${state.displayRules.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        state.displayRules.forEach { rule ->
                            Text(
                                text = rule.pattern,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showDemoBarrier) {
        DemoDataBarrierDialog(
            onConfirmDelete = viewModel::confirmDeleteDemoData,
            onDismiss = viewModel::dismissDemoBarrier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankDropdown(
    banks: List<com.smsexpensetracker.domain.model.Bank>,
    selectedBankId: Long?,
    onBankChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = banks.find { it.id == selectedBankId }?.name ?: "Auto-detect",
            onValueChange = {},
            readOnly = true,
            label = { Text("Bank") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Auto-detect") },
                onClick = { onBankChange(null); expanded = false }
            )
            banks.forEach { bank ->
                DropdownMenuItem(
                    text = { Text(bank.name) },
                    onClick = { onBankChange(bank.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: com.smsexpensetracker.domain.value.ParsedResult,
    bankName: String,
    onAddAsTransaction: () -> Unit,
    isSaving: Boolean
) {
    val isDark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    val canAdd = result.bankId != null && result.amount > 0L

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Parsed result",
                    style = MaterialTheme.typography.titleSmall
                )
                ConfidenceBadge(confidence = result.confidence, isDark = isDark)
            }

            ResultField(label = "Amount", value = formatPaisa(result.amount))
            ResultField(
                label = "Type",
                value = if (result.type == TransactionType.CREDIT) "Credit" else "Debit"
            )
            ResultField(label = "Description", value = result.description)
            ResultField(label = "Bank", value = bankName)

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onAddAsTransaction,
                enabled = canAdd && !isSaving,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSaving) "Adding..." else "Add as Transaction")
            }
        }
    }
}

@Composable
private fun ResultField(label: String, value: String) {
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

@Composable
private fun ConfidenceBadge(confidence: Float, isDark: Boolean) {
    val (color, background) = when {
        confidence >= 0.7f -> if (isDark) Green80 to Green40 else Green40 to Green80
        confidence >= 0.4f -> if (isDark) Amber80 to Amber40 else Amber40 to Amber80
        else -> if (isDark) Red80 to Red40 else Red40 to Red80
    }
    Box(
        modifier = Modifier
            .background(background.copy(alpha = 0.2f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
