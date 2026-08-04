package com.smsexpensetracker.ui.screens.unparsed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnparsedSmsScreen(
    onBack: () -> Unit,
    onFix: (Long, String) -> Unit,
    viewModel: UnparsedSmsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val failedLogs by viewModel.failedLogs.collectAsState()
    val parseLogs by viewModel.parseLogs.collectAsState()
    val banks by viewModel.banks.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.syncMessage) {
        state.syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSyncMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unparsed SMS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filter == UnparsedFilter.ALL,
                    onClick = { viewModel.setFilter(UnparsedFilter.ALL) },
                    label = { Text("All") },
                    shape = MaterialTheme.shapes.small
                )
                FilterChip(
                    selected = state.filter == UnparsedFilter.FAILED,
                    onClick = { viewModel.setFilter(UnparsedFilter.FAILED) },
                    label = { Text("Failed") },
                    shape = MaterialTheme.shapes.small
                )
            }

            Button(
                onClick = viewModel::resync,
                enabled = !state.isSyncing,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSyncing) "Syncing…" else "Re-sync now")
            }

            if (state.filter == UnparsedFilter.FAILED) {
                if (failedLogs.isEmpty()) {
                    Text(
                        text = "No unparsed SMS",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(failedLogs, key = { it.smsBody }) { failed ->
                            FailedSmsCard(failed = failed, banks = banks, onFix = onFix)
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(parseLogs, key = { it.id }) { log ->
                        ParseLogCard(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedSmsCard(
    failed: FailedSms,
    banks: List<Bank>,
    onFix: (Long, String) -> Unit
) {
    val bankName = banks.firstOrNull { it.id == failed.bankId }?.name
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = failed.smsSender,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                bankName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = "FAILED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = failed.smsBody,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Failed ${failed.failCount}x · ${failed.lastParsedAt}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            failed.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            val bankId = failed.bankId
            if (bankId != null) {
                Button(
                    onClick = { onFix(bankId, failed.smsBody) },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                ) {
                    Text("Fix")
                }
            } else {
                Text(
                    text = "No matching bank — add it in Banks & Rules first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ParseLogCard(log: ParseLog) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = log.smsSender,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = log.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (log.status == ParseStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                text = log.smsBody,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = log.parsedAt.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
