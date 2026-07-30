package com.smsexpensetracker.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFilterChips(
    filterType: TransactionType?,
    onFilterTypeChange: (TransactionType?) -> Unit,
    banks: List<Bank>,
    selectedBankId: Long?,
    onBankChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var bankExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = filterType == null,
            onClick = { onFilterTypeChange(null) },
            label = { Text("All") }
        )
        FilterChip(
            selected = filterType == TransactionType.CREDIT,
            onClick = { onFilterTypeChange(TransactionType.CREDIT) },
            label = { Text("Credit") }
        )
        FilterChip(
            selected = filterType == TransactionType.DEBIT,
            onClick = { onFilterTypeChange(TransactionType.DEBIT) },
            label = { Text("Debit") }
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            ExposedDropdownMenuBox(
                expanded = bankExpanded,
                onExpandedChange = { bankExpanded = it }
            ) {
                OutlinedTextField(
                    value = banks.find { it.id == selectedBankId }?.name ?: "All Banks",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                ExposedDropdownMenu(
                    expanded = bankExpanded,
                    onDismissRequest = { bankExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Banks") },
                        onClick = { onBankChange(null); bankExpanded = false }
                    )
                    banks.forEach { bank ->
                        DropdownMenuItem(
                            text = { Text(bank.name) },
                            onClick = { onBankChange(bank.id); bankExpanded = false }
                        )
                    }
                }
            }
        }
    }
}
