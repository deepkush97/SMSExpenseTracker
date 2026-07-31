package com.smsexpensetracker.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.ui.theme.Green40
import com.smsexpensetracker.ui.theme.Green80
import com.smsexpensetracker.ui.theme.Red40
import com.smsexpensetracker.ui.theme.Red80
import com.smsexpensetracker.ui.util.formatPaisa
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun MonthlySummaryBanner(
    yearMonth: YearMonth,
    credits: Long,
    debits: Long,
    net: Long,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canGoNext = yearMonth < YearMonth.now()
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        tint = if (canGoNext) colorScheme.onSurfaceVariant else colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryChip(
                    label = "Credits",
                    amount = credits,
                    icon = Icons.Filled.TrendingUp,
                    color = Green80,
                    containerColor = lerp(colorScheme.surfaceContainerHigh, Green40, 0.12f),
                    modifier = Modifier.weight(1f)
                )
                SummaryChip(
                    label = "Debits",
                    amount = debits,
                    icon = Icons.Filled.TrendingDown,
                    color = Red80,
                    containerColor = lerp(colorScheme.surfaceContainerHigh, Red40, 0.12f),
                    modifier = Modifier.weight(1f)
                )
                SummaryChip(
                    label = "Net",
                    amount = net,
                    icon = if (net >= 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                    color = if (net >= 0) Green40 else Red40,
                    containerColor = lerp(colorScheme.surfaceContainerHigh, if (net >= 0) Green40 else Red40, 0.12f),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    amount: Long,
    icon: ImageVector,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(start = 1.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatPaisa(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
