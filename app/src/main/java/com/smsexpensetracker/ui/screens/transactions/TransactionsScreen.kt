package com.smsexpensetracker.ui.screens.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.smsexpensetracker.ui.components.EmptyState
import com.smsexpensetracker.ui.components.TransactionRow
import com.smsexpensetracker.ui.components.rememberSpringPressScale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    onNavigateToManualEntry: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                    onAction = { /* TODO: trigger sync when SyncUseCase is ready */ }
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        bottom = 88.dp
                    )
                ) {
                    item(key = "summary") {
                        MonthlySummaryBanner(
                            yearMonth = state.currentMonth,
                            credits = state.monthlyCredits,
                            debits = state.monthlyDebits,
                            net = state.netAmount,
                            onPrevMonth = { viewModel.onMonthChange(state.currentMonth.minusMonths(1)) },
                            onNextMonth = { viewModel.onMonthChange(state.currentMonth.plusMonths(1)) }
                        )
                    }
                    item(key = "search") {
                        TransactionSearchBar(
                            query = state.searchQuery,
                            onQueryChange = viewModel::onSearchQueryChange
                        )
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
                                val bankName = state.banks.find { it.id == tx.bankId }?.name ?: "Unknown"
                                val category = tx.categoryId?.let { cid -> state.categories.find { it.id == cid } }
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
                                        subtitle = "$bankName · ${tx.transactionDate.format(DateTimeFormatter.ofPattern("dd MMM, hh:mm a"))}"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.selectedTransaction?.let { tx ->
        TransactionDetailSheet(
            transaction = tx,
            banks = state.banks,
            categories = state.categories,
            onCategoryChange = viewModel::onCategoryChange,
            onDismiss = viewModel::onDismissSheet
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
