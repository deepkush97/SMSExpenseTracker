package com.smsexpensetracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun SMSExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    seedColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val useSystemDynamic = seedColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val baseColorScheme = if (useSystemDynamic) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot
        )
    }

    val colorScheme = if (darkTheme && pureBlack) {
        baseColorScheme.copy(surface = Color.Black, background = Color.Black)
    } else {
        baseColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
