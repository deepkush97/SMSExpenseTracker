package com.smsexpensetracker.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryColorSchemeTest {

    @Test
    fun brightColorGetsDarkReadableForeground() {
        val bright = Color(0xFFFDD835)
        val fg = readableOnColor(bright)
        assertTrue("bright category should produce dark text", fg.luminance() < bright.luminance())
    }

    @Test
    fun darkColorGetsLightReadableForeground() {
        val dark = Color(0xFF0B57D0)
        val fg = readableOnColor(dark)
        assertTrue("dark category should produce light text", fg.luminance() > dark.luminance())
    }

    @Test
    fun extremesResolveSensibly() {
        assertTrue(readableOnColor(Color.White).luminance() < Color.White.luminance())
        assertTrue(readableOnColor(Color.Black).luminance() > Color.Black.luminance())
    }

    @Test
    fun chipBackgroundBlendsCategoryIntoContainer() {
        val container = Color(0xFFE9E9EB)
        val category = Color(0xFF0B57D0)
        val colors = categoryChipColors(category, container)
        assertTrue(colors.background != container)
        assertTrue(colors.background != category)
        assertTrue(colors.foreground != colors.background)
    }

    @Test
    fun readableOnColorIsDeterministic() {
        val color = Color(0xFF16A34A)
        assertEquals(readableOnColor(color), readableOnColor(color))
    }
}
