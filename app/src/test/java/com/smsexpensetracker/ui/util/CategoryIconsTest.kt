package com.smsexpensetracker.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Restaurant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryIconsTest {

    @Test
    fun `catalog has between 120 and 200 entries`() {
        assertTrue(CATEGORY_ICONS.size in 120..200)
    }

    @Test
    fun `every icon name is unique`() {
        val names = CATEGORY_ICONS.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `catalog includes all 14 legacy seed icon keys`() {
        val names = CATEGORY_ICONS.map { it.name }
        listOf(
            "restaurant", "shopping_cart", "local_gas_station", "receipt",
            "shopping_bag", "movie", "local_hospital", "directions_car",
            "school", "home", "flight", "payments", "trending_up", "category"
        ).forEach { assertTrue("missing $it", it in names) }
    }

    @Test
    fun `empty query returns the full catalog`() {
        assertEquals(CATEGORY_ICONS, searchIcons(""))
        assertEquals(CATEGORY_ICONS, searchIcons("   "))
    }

    @Test
    fun `search matches an icon name`() {
        assertTrue(searchIcons("home").any { it.name == "home" })
    }

    @Test
    fun `search matches a keyword alias`() {
        assertTrue(searchIcons("food").any { it.name == "restaurant" })
    }

    @Test
    fun `search is case insensitive`() {
        assertTrue(searchIcons("FOOD").any { it.name == "restaurant" })
    }

    @Test
    fun `search ignores underscores`() {
        assertTrue(searchIcons("shoppingcart").any { it.name == "shopping_cart" })
    }

    @Test
    fun `search returns empty for no match`() {
        assertTrue(searchIcons("zzzznotanicon").isEmpty())
    }

    @Test
    fun `materialIcon returns the vector for a known name`() {
        assertSame(Icons.Filled.Restaurant, materialIcon("restaurant"))
    }

    @Test
    fun `materialIcon falls back to category icon for unknown name`() {
        assertSame(Icons.Filled.Category, materialIcon("does_not_exist"))
    }
}
