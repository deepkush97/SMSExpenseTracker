package com.smsexpensetracker.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

fun readableOnColor(color: Color): Color =
    if (color.luminance() > 0.5f) {
        lerp(color, Color.Black, 0.55f)
    } else {
        lerp(color, Color.White, 0.55f)
    }

fun categoryChipColors(categoryColor: Color, container: Color): CategoryChipColors =
    CategoryChipColors(
        background = lerp(container, categoryColor, 0.18f),
        foreground = readableOnColor(categoryColor)
    )

data class CategoryChipColors(val background: Color, val foreground: Color)
