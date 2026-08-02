package com.smsexpensetracker.ui.screens.banks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.domain.model.Bank
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankManagementScreen(
    onBack: () -> Unit = {},
    onBankClick: (Bank) -> Unit = {},
    viewModel: BankManagementViewModel = hiltViewModel()
) {
    val banks by viewModel.banks.collectAsState()
    val transactionCounts by viewModel.transactionCounts.collectAsState()
    var editing by remember { mutableStateOf<Bank?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Bank?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Banks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add bank")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(banks, key = { it.id }) { bank ->
                val count = transactionCounts[bank.id] ?: 0
                val canDelete = count == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBankClick(bank) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bank.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = bank.smsSender + if (count > 0) " · $count transactions" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { editing = bank }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${bank.name}")
                    }
                    IconButton(
                        onClick = {
                            if (canDelete) deleting = bank
                            else scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Cannot delete — $count transactions use this bank",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete ${bank.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        BankDialog(
            existing = null,
            allBanks = banks,
            onSave = { name, sender ->
                viewModel.addBank(name, sender)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    editing?.let { bank ->
        BankDialog(
            existing = bank,
            allBanks = banks,
            onSave = { name, sender ->
                viewModel.updateBank(bank.copy(name = name, smsSender = sender))
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { bank ->
        BankDeleteDialog(
            bank = bank,
            onConfirm = {
                viewModel.deleteBank(bank)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}
