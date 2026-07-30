package com.smsexpensetracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    secondary = Green40,
    onSecondary = Color.White,
    secondaryContainer = Green80,
    error = Red40,
    onError = Color.White,
    errorContainer = Red80,
    surface = SurfaceLight,
    onSurface = Color(0xFF1C1C1E),
    background = SurfaceLight,
    onBackground = Color(0xFF1C1C1E),
    outline = Gray80
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Color(0xFF003A9A),
    primaryContainer = Color(0xFF0046B5),
    secondary = Green80,
    onSecondary = Color(0xFF003D1A),
    secondaryContainer = Color(0xFF005B26),
    error = Red80,
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    surface = SurfaceDark,
    onSurface = Color(0xFFE5E5E5),
    background = SurfaceDark,
    onBackground = Color(0xFFE5E5E5),
    outline = Gray80
)

@Composable
fun SMSExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
