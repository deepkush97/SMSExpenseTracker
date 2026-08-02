package com.smsexpensetracker.ui.screens.banks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankDetailScreen(
    onBack: () -> Unit = {},
    viewModel: BankDetailViewModel = hiltViewModel()
) {
    val bank by viewModel.bank.collectAsState()
    val rules by viewModel.rules.collectAsState()
    var editing by remember { mutableStateOf<SmsRule?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SmsRule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bank?.name ?: "Bank") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add rule")
            }
        }
    ) { innerPadding ->
        if (rules.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Add,
                title = "No rules yet",
                subtitle = "Tap + to add an SMS rule for ${bank?.name ?: "this bank"}",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.description, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = rule.pattern,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = rule.isActive,
                            onCheckedChange = { viewModel.setRuleActive(rule, it) }
                        )
                        IconButton(onClick = { editing = rule }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${rule.description}")
                        }
                        IconButton(onClick = { deleting = rule }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete ${rule.description}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        RuleDialog(
            existing = null,
            onSave = { description, pattern ->
                viewModel.addRule(description, pattern)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    editing?.let { rule ->
        RuleDialog(
            existing = rule,
            onSave = { description, pattern ->
                viewModel.updateRule(rule.copy(description = description, pattern = pattern))
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { rule ->
        RuleDeleteDialog(
            rule = rule,
            onConfirm = {
                viewModel.deleteRule(rule)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}
