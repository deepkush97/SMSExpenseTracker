package com.smsexpensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.ui.theme.Green40
import com.smsexpensetracker.ui.theme.Green80
import com.smsexpensetracker.ui.util.formatAmountWithSign
import com.smsexpensetracker.ui.util.readableOnColor
import java.time.format.DateTimeFormatter

@Composable
fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    categoryName: String? = null,
    categoryColor: Color? = null,
    subtitle: String = transaction.transactionDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.onSurface.luminance() > 0.5f
    val creditColor = if (isDark) Green80 else Green40
    val avatarBackground = if (categoryColor != null) {
        lerp(colorScheme.surfaceContainerHigh, categoryColor, 0.18f)
    } else {
        colorScheme.surfaceContainerHighest
    }
    val avatarForeground = if (categoryColor != null) readableOnColor(categoryColor) else colorScheme.onSurfaceVariant
    val avatarText = (categoryName ?: transaction.description).firstOrNull()?.uppercase() ?: "?"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(avatarBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = avatarText,
                style = MaterialTheme.typography.labelLarge,
                color = avatarForeground
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatAmountWithSign(transaction.amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (transaction.transactionType == TransactionType.CREDIT) creditColor else colorScheme.onSurface
        )
    }
}
