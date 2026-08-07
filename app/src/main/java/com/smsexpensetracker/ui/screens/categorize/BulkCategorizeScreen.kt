package com.smsexpensetracker.ui.screens.categorize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkCategorizeScreen(
    onBack: () -> Unit,
    viewModel: BulkCategorizeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bulk Categorize") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.uncategorizedCount == 0 -> {
                EmptyState(
                    icon = Icons.Filled.Sell,
                    title = "No uncategorized transactions",
                    subtitle = "Every transaction already has a category.",
                    modifier = Modifier.padding(innerPadding)
                )
            }

            else -> {
                BulkCategorizeContent(
                    state = state,
                    onCategorySelected = viewModel::loadSuggestions,
                    onEnabledChanged = viewModel::setEnabled,
                    onApply = viewModel::apply,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun BulkCategorizeContent(
    state: BulkCategorizeUiState,
    onCategorySelected: (Int, Long?) -> Unit,
    onEnabledChanged: (Int, Boolean) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(state.suggestions) { index, suggestion ->
            SuggestionRow(
                index = index,
                suggestion = suggestion,
                categories = state.categories,
                onCategorySelected = onCategorySelected,
                onEnabledChanged = onEnabledChanged
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = when {
                        state.isApplying -> "Categorizing…"
                        state.hasApplied ->
                            "${state.categorizedCount} categorized, ${state.remainingCount} uncategorized"
                        else -> "Categorizes ~${state.previewCount} of ${state.uncategorizedCount} uncategorized"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.hasApplied) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.conflicts.isNotEmpty()) {
                    Text(
                        text = state.conflicts.joinToString("  ") { (a, b) -> "Overlapping keywords: $a and $b" } +
                            " — rules apply in order, so one may match the other's transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = onApply,
                    enabled = !state.isApplying && state.previewCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isApplying) "Applying…" else "Apply")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionRow(
    index: Int,
    suggestion: SuggestionUi,
    categories: List<Category>,
    onCategorySelected: (Int, Long?) -> Unit,
    onEnabledChanged: (Int, Boolean) -> Unit
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == suggestion.chosenCategoryId }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = suggestion.keyword,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = suggestion.enabled,
                    onCheckedChange = { onEnabledChanged(index, it) }
                )
            }

            Text(
                text = "Appears in ${suggestion.transactionCount} transaction${if (suggestion.transactionCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "Uncategorized",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    enabled = suggestion.enabled,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    singleLine = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                onCategorySelected(index, cat.id)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}