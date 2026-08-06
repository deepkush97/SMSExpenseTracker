package com.smsexpensetracker.ui.screens.dashboard

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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.smsexpensetracker.ui.TestTags
import com.smsexpensetracker.ui.components.TransactionRow
import com.smsexpensetracker.ui.components.rememberSmsSyncPermission
import com.smsexpensetracker.ui.components.rememberSpringPressScale
import com.smsexpensetracker.ui.onboarding.OnboardingActionsViewModel
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onTransactionClick: (Long) -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingActionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    var showGetStartedCard by remember { mutableStateOf(true) }

    val requestSync = rememberSmsSyncPermission(
        onGranted = { onboardingViewModel.sync() },
        onDenied = {}
    )

    if (state.isLoading) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showGetStartedCard && state.recentTransactions.isEmpty()) {
            item(key = "getStarted") {
                GetStartedCard(
                    isBusy = onboardingState.isBusy,
                    onDemoData = onboardingViewModel::loadDemoData,
                    onSyncSms = requestSync,
                    onDismiss = { showGetStartedCard = false }
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        label = "Total Spent",
                        amountPaisa = state.totalSpent,
                        icon = Icons.AutoMirrored.Filled.TrendingDown,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        label = "Total Received",
                        amountPaisa = state.totalReceived,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        color = com.smsexpensetracker.ui.theme.Green40,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (state.bankChartData.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    BankChart(data = state.bankChartData)
                }
            }
        }

        if (state.monthlyChartData.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    MonthlyChart(data = state.monthlyChartData)
                }
            }
        }

        if (state.categoryChartData.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    CategoryChart(data = state.categoryChartData)
                }
            }
        }

        if (state.recentTransactions.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(state.recentTransactions) { transaction ->
                val (interactionSource, scale) = rememberSpringPressScale()
                val category =
                    transaction.categoryId?.let { cid -> state.categories.find { it.id == cid } }
                val bankName = state.banks.find { it.id == transaction.bankId }?.name
                Card(
                    onClick = { onTransactionClick(transaction.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    interactionSource = interactionSource
                ) {
                    TransactionRow(
                        transaction = transaction,
                        categoryName = category?.name,
                        categoryColor = category?.let { Color(it.color) },
                        subtitle = buildString {
                            if (bankName != null) {
                                append(bankName)
                                append(" · ")
                            }
                            append(
                                transaction.transactionDate.format(
                                    DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
                                )
                            )
                        }
                    )
                }
            }
            item(key = "viewAll") {
                TextButton(
                    onClick = onNavigateToTransactions,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("View All Transactions")
                }
            }
        }
    }
}

@Composable
private fun GetStartedCard(
    isBusy: Boolean,
    onDemoData: () -> Unit,
    onSyncSms: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(TestTags.GET_STARTED_CARD),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Get started",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                }
            }
            Text(
                text = "Load sample data to explore, or sync your real bank SMS.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onDemoData,
                        modifier = Modifier.weight(1f)
                    ) { Text("Try demo data") }
                    OutlinedButton(
                        onClick = onSyncSms,
                        modifier = Modifier.weight(1f)
                    ) { Text("Sync SMS") }
                }
            }
        }
    }
}
