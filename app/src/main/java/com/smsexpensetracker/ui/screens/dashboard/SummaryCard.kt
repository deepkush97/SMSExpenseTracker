package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.ui.theme.AppAnimation
import com.smsexpensetracker.ui.util.formatPaisa

@Composable
fun SummaryCard(
    label: String,
    amountPaisa: Long,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedAmount by animateValueAsState(
        targetValue = amountPaisa,
        animationSpec = AppAnimation.spring(),
        typeConverter = LongVectorConverter,
        label = "amount"
    )

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = lerp(MaterialTheme.colorScheme.surfaceContainerLow, color, 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                )
            }
            Text(
                text = formatPaisa(animatedAmount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

private val LongVectorConverter: TwoWayConverter<Long, AnimationVector1D> = TwoWayConverter(
    convertToVector = { AnimationVector1D(it.toFloat()) },
    convertFromVector = { it.value.toLong() }
)
