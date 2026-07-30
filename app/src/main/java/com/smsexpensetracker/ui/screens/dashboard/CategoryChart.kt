package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import com.patrykandpatrick.vico.compose.pie.rememberPieChart

@Composable
fun CategoryChart(
    data: List<CategoryPieItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Category Breakdown",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (data.isEmpty()) {
            Text(
                text = "No categorized spending yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val modelProducer = remember { PieChartModelProducer() }
            val values = data.map { it.amount / 100.0 }

            LaunchedEffect(values) {
                modelProducer.runTransaction {
                    pieSeries { series(values) }
                }
            }

            PieChartHost(
                rememberPieChart(),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}
