package com.smsexpensetracker.ui.screens.categorize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.ui.TestTags
import com.smsexpensetracker.ui.components.EmptyState
import com.smsexpensetracker.ui.util.formatPaisa
import java.time.format.DateTimeFormatter

@Composable
fun CategorizeScreen(
    modifier: Modifier = Modifier,
    onBulkCategorize: () -> Unit = {},
    viewModel: CategorizeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val current = state.current

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        when {
            state.queue.isEmpty() -> {
                EmptyState(
                    icon = Icons.Filled.Sell,
                    title = "No transactions yet",
                    subtitle = "Sync SMS or add a transaction to start categorizing.",
                    modifier = Modifier.padding(innerPadding)
                )
            }

            current == null -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "All done!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Categorized ${state.assignedCount} of ${state.queue.size} transactions.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = viewModel::reset) {
                        Text("Start over")
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val uncategorizedCount = state.queue.count { it.categoryId == null }
                    if (uncategorizedCount > 0) {
                        Button(
                            onClick = onBulkCategorize,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestTags.BULK_CATEGORIZE_BANNER)
                        ) {
                            Text("$uncategorizedCount uncategorized — Categorize automatically")
                        }
                    }
                    Text(
                        text = "Categorize",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.index + 1} of ${state.queue.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    CategoryCard(
                        transaction = current,
                        bankName = state.banks.find { it.id == current.bankId }?.name ?: "Unknown",
                        categories = state.categories,
                        selectedCategoryId = current.categoryId,
                        lastCategoryId = state.lastCategoryId,
                        onCategorySelected = viewModel::assignCategory,
                        onAssignSameAsPrevious = viewModel::assignSameAsPrevious
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = viewModel::skip) {
                            Text("Skip")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(
    transaction: Transaction,
    bankName: String,
    categories: List<Category>,
    selectedCategoryId: Long?,
    lastCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit,
    onAssignSameAsPrevious: () -> Unit
) {
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = formatPaisa(transaction.amount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            DetailRow("Type", transaction.transactionType.name)
            DetailRow("Description", transaction.description)
            DetailRow("Bank", bankName)
            DetailRow(
                "Date",
                transaction.transactionDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
            )
            DetailRow(
                "Current",
                selectedCategory?.name ?: "Uncategorized"
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedCategoryId == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("None") }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = cat.id == selectedCategoryId,
                        onClick = { onCategorySelected(cat.id) },
                        label = { Text(cat.name) }
                    )
                }
            }

            TextButton(
                onClick = onAssignSameAsPrevious,
                enabled = lastCategoryId != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Same as previous")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
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
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
