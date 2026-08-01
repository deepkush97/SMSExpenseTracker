package com.smsexpensetracker.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import com.smsexpensetracker.ui.theme.Green40
import com.smsexpensetracker.ui.theme.Green80
import com.smsexpensetracker.ui.theme.Red40
import com.smsexpensetracker.ui.theme.Red80
import com.smsexpensetracker.ui.util.formatPaisa
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun MonthlyOverviewCard(
    yearMonth: YearMonth,
    credits: Long,
    debits: Long,
    net: Long,
    categoryData: List<MonthlyCategoryItem>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canGoNext = yearMonth < YearMonth.now()
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.onSurface.luminance() > 0.5f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Month header with arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevMonth) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onNextMonth, enabled = canGoNext) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = if (canGoNext) colorScheme.onSurfaceVariant else colorScheme.onSurface.copy(
                            alpha = 0.3f
                        )
                    )
                }
            }

            // Summary chips — compact single-row layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompactStat(
                    label = "Credits",
                    amount = credits,
                    color = if (isDark) Green80 else Green40
                )
                CompactStat(label = "Debits", amount = debits, color = if (isDark) Red80 else Red40)
                CompactStat(
                    label = "Net",
                    amount = net,
                    color = if (net >= 0) {
                        if (isDark) Green80 else Green40
                    } else {
                        if (isDark) Red80 else Red40
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Pie chart + legend side by side
            if (categoryData.isEmpty()) {
                Text(
                    text = "No categorized spending this month",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            } else {
                val modelProducer = remember { PieChartModelProducer() }
                val values = categoryData.map { it.amount / 100.0 }
                val slices = categoryData.map { PieChart.Slice(fill = Fill(Color(it.color))) }

                LaunchedEffect(values) {
                    modelProducer.runTransaction {
                        pieSeries { series(values) }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    PieChartHost(
                        rememberPieChart(
                            sliceProvider = PieChart.SliceProvider.series(slices)
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier.size(120.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val total = categoryData.sumOf { it.amount }
                        categoryData.forEach { item ->
                            val pct = if (total > 0) item.amount * 100.0 / total else 0.0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(item.color))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = item.categoryName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatPaisa(item.amount)}  %.1f%%".format(pct),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStat(
    label: String,
    amount: Long,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatPaisa(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
