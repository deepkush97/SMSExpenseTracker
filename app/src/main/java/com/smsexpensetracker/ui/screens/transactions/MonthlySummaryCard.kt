package com.smsexpensetracker.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.ui.theme.Green40
import com.smsexpensetracker.ui.theme.Red40
import com.smsexpensetracker.ui.util.formatPaisa

@Composable
fun MonthlySummaryCard(
    credits: Long,
    debits: Long,
    net: Long,
    txCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly Summary",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryStat(label = "Credits", amount = credits, color = Green40)
                SummaryStat(label = "Debits", amount = debits, color = Red40)
                SummaryStat(
                    label = "Net",
                    amount = net,
                    color = if (net >= 0) Green40 else Red40,
                    bold = true
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Transactions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = txCount.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(
    label: String,
    amount: Long,
    color: androidx.compose.ui.graphics.Color,
    bold: Boolean = false
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
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = color
        )
    }
}
