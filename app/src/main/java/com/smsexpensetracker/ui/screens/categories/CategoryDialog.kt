package com.smsexpensetracker.ui.screens.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.ui.util.CATEGORY_COLORS
import com.smsexpensetracker.ui.util.CATEGORY_ICONS
import com.smsexpensetracker.ui.util.searchIcons
import com.smsexpensetracker.ui.util.validateCategoryName

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    existing: Category?,
    allCategories: List<Category>,
    onSave: (name: String, icon: String, color: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: CATEGORY_ICONS.first().name) }
    var iconQuery by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(existing?.color ?: CATEGORY_COLORS.first()) }

    val nameError = validateCategoryName(name, allCategories, existing?.id)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text("Color", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        CATEGORY_COLORS.forEach { value ->
                            val selected = value == color
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(value))
                                    .then(
                                        if (selected) Modifier.border(
                                            BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                                            CircleShape
                                        ) else Modifier
                                    )
                                    .clickable { color = value }
                            )
                        }
                    }
                }
                Column {
                    Text("Icon", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = iconQuery,
                        onValueChange = { iconQuery = it },
                        label = { Text("Search icons") },
                        singleLine = true,
                        trailingIcon = {
                            if (iconQuery.isNotEmpty()) {
                                IconButton(onClick = { iconQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(top = 8.dp)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(searchIcons(iconQuery), key = { it.name }) { entry ->
                                val selected = entry.name == icon
                                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                Icon(
                                    imageVector = entry.imageVector,
                                    contentDescription = entry.name,
                                    tint = tint,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            icon = entry.name
                                            iconQuery = ""
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), icon, color) },
                enabled = nameError == null
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
