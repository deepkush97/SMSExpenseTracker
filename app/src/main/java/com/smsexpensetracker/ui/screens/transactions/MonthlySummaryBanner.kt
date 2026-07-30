package com.smsexpensetracker.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.ui.screens.dashboard.formatPaisa
import com.smsexpensetracker.ui.theme.Green40
import com.smsexpensetracker.ui.theme.Red40
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

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onNextMonth, enabled = canGoNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Credits", style = MaterialTheme.typography.labelSmall)
                    Text(formatPaisa(credits), style = MaterialTheme.typography.bodyLarge, color = Green40)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Debits", style = MaterialTheme.typography.labelSmall)
                    Text(formatPaisa(debits), style = MaterialTheme.typography.bodyLarge, color = Red40)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Net", style = MaterialTheme.typography.labelSmall)
                    Text(
                        formatPaisa(net),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (net >= 0) Green40 else Red40,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
