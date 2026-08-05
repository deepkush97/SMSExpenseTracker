package com.smsexpensetracker.ui.screens.transactions

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.smsexpensetracker.data.sms.PermissionManager
import com.smsexpensetracker.ui.components.DemoDataBarrierDialog
import com.smsexpensetracker.ui.components.EmptyState
import com.smsexpensetracker.ui.components.TransactionRow
import com.smsexpensetracker.ui.components.rememberSpringPressScale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    onNavigateToManualEntry: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val showDemoBarrier by viewModel.showDemoBarrier.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionManager = remember { PermissionManager() }
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.sync()
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "SMS access is needed to sync transactions",
                    actionLabel = "Open Settings",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    permissionManager.openSettings(context)
                }
            }
        }
    }

    fun beginSync() {
        if (permissionManager.hasPermission(context)) {
            viewModel.sync()
        } else if (permissionManager.shouldShowRationale(context as? Activity)) {
            showRationale = true
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    Scaffold(
        modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToManualEntry) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.displayedTransactions.isEmpty() && state.searchQuery.isBlank() && state.filterType == null && state.selectedBankId == null -> {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "No transactions yet",
                    subtitle = "Sync your SMS to get started, or tap + to add manually",
                    actionLabel = "Sync SMS",
                    onAction = { beginSync() }
                )
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    item(key = "overview") {
                        Box(modifier = Modifier.padding(top = 8.dp)) {
                            MonthlyOverviewCard(
                                yearMonth = state.currentMonth,
                                credits = state.monthlyCredits,
                                debits = state.monthlyDebits,
                                net = state.netAmount,
                                categoryData = state.monthlyCategoryBreakdown,
                                onPrevMonth = { viewModel.onMonthChange(state.currentMonth.minusMonths(1)) },
                                onNextMonth = { viewModel.onMonthChange(state.currentMonth.plusMonths(1)) }
                            )
                        }
                    }
                    item(key = "searchSpacer") { Spacer(Modifier.height(12.dp)) }
                    item(key = "search") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TransactionSearchBar(
                                query = state.searchQuery,
                                onQueryChange = viewModel::onSearchQueryChange,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { beginSync() },
                                enabled = !state.isSyncing,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                if (state.isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Sync SMS")
                                }
                            }
                        }
                    }
                    item(key = "filterSpacer") { Spacer(Modifier.height(8.dp)) }
                    item(key = "filters") {
                        TransactionFilterChips(
                            filterType = state.filterType,
                            onFilterTypeChange = viewModel::onFilterTypeChange,
                            banks = state.banks,
                            selectedBankId = state.selectedBankId,
                            onBankChange = viewModel::onBankChange
                        )
                    }
                    item(key = "chipSpacer") { Spacer(Modifier.height(4.dp)) }

                    if (state.displayedTransactions.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                icon = Icons.Filled.Search,
                                title = "No results",
                                subtitle = "Try a different search or filter",
                                modifier = Modifier.height(300.dp)
                            )
                        }
                    } else {
                        val grouped = state.displayedTransactions.groupBy { tx ->
                            val date = tx.transactionDate.toLocalDate()
                            val today = LocalDate.now()
                            when {
                                date == today -> "Today"
                                date == today.minusDays(1) -> "Yesterday"
                                else -> date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                            }
                        }
                        grouped.forEach { (header, txs) ->
                            item(key = "header_$header") { DateSectionHeader(header) }
                            items(txs, key = { it.id }) { tx ->
                                val (interactionSource, scale) = rememberSpringPressScale()
                                val bankName =
                                    state.banks.find { it.id == tx.bankId }?.name ?: "Unknown"
                                val category =
                                    tx.categoryId?.let { cid -> state.categories.find { it.id == cid } }
                                Card(
                                    onClick = { viewModel.onTransactionClick(tx) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .graphicsLayer { scaleX = scale; scaleY = scale },
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    interactionSource = interactionSource
                                ) {
                                    TransactionRow(
                                        transaction = tx,
                                        categoryName = category?.name,
                                        categoryColor = category?.let { Color(it.color) },
                                        subtitle = "$bankName · ${
                                            tx.transactionDate.format(
                                                DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
                                            )
                                        }"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(state.syncMessage) {
        val message = state.syncMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSyncMessage()
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Allow SMS access?") },
            text = { Text("SMS Expense Tracker reads your bank SMS to extract transaction details. SMS data stays on your device.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now") }
            }
        )
    }

    state.selectedTransaction?.let { tx ->
        TransactionDetailSheet(
            transaction = tx,
            banks = state.banks,
            categories = state.categories,
            onCategoryChange = { _, _ -> },
            onDismiss = viewModel::onDismissSheet
        )
    }

    if (showDemoBarrier) {
        DemoDataBarrierDialog(
            onConfirmDelete = viewModel::confirmDeleteDemoData,
            onDismiss = viewModel::dismissDemoBarrier
        )
    }
}

@Composable
private fun DateSectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}
