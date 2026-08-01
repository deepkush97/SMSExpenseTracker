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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.ui.theme.Green40
import com.smsexpensetracker.ui.theme.Green80
import com.smsexpensetracker.ui.theme.Red40
import com.smsexpensetracker.ui.theme.Red80
import com.smsexpensetracker.ui.util.categoryChipColors
import com.smsexpensetracker.ui.util.formatAmountWithSign

@Composable
fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    categoryName: String? = null,
    categoryColor: Color? = null,
    subtitle: String = ""
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.onSurface.luminance() > 0.5f
    val isCredit = transaction.transactionType == TransactionType.CREDIT

    val transactionAmount = if (isCredit) transaction.amount else -transaction.amount

    val avatarBg = if (isCredit) {
        if (isDark) Green80.copy(alpha = 0.18f) else Green40.copy(alpha = 0.18f)
    } else {
        if (isDark) Red80.copy(alpha = 0.18f) else Red40.copy(alpha = 0.18f)
    }
    val avatarFg = if (isCredit) {
        if (isDark) Green80 else Green40
    } else {
        if (isDark) Red80 else Red40
    }

    val chipBg = if (categoryColor != null) {
        categoryChipColors(categoryColor, colorScheme.surfaceContainerHigh).background
    } else {
        colorScheme.surfaceContainerHighest
    }
    val chipFg = if (categoryColor != null) {
        categoryChipColors(categoryColor, colorScheme.surfaceContainerHigh).foreground
    } else {
        colorScheme.onSurfaceVariant
    }
    val chipLabel = categoryName ?: transaction.description.take(12)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(avatarBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCredit) "C" else "D",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = avatarFg
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(chipBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = chipLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = chipFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = transaction.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = formatAmountWithSign(transactionAmount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isCredit) {
                if (isDark) Green80 else Green40
            } else {
                if (isDark) Red80 else Red40
            }
        )
    }
}
