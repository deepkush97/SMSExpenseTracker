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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill

@Composable
fun MonthlyChart(
    data: List<MonthlyLineItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Monthly Trend",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = "Credits (blue) vs Debits (green) over time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (data.isEmpty()) {
            Text(
                text = "No monthly data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val modelProducer = remember { CartesianChartModelProducer() }
            val creditSeries = data.map { it.credit / 100.0 }
            val debitSeries = data.map { it.debit / 100.0 }

            LaunchedEffect(creditSeries, debitSeries) {
                modelProducer.runTransaction {
                    lineModel {
                        series(y = creditSeries)
                        series(y = debitSeries)
                    }
                }
            }

            val colorScheme = MaterialTheme.colorScheme
            val axisLabel = rememberAxisLabelComponent(
                style = TextStyle(color = colorScheme.onSurfaceVariant, fontSize = 10.sp)
            )
            val axisLine = rememberAxisLineComponent(fill = Fill(colorScheme.outlineVariant))
            val axisTick = rememberAxisTickComponent(fill = Fill(colorScheme.outlineVariant))
            val axisGuideline = rememberAxisGuidelineComponent(fill = Fill(colorScheme.surfaceVariant))

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(
                        line = axisLine,
                        label = axisLabel,
                        tick = axisTick,
                        guideline = axisGuideline
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        line = axisLine,
                        label = axisLabel,
                        tick = axisTick,
                        guideline = axisGuideline
                    ),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }
    }
}
