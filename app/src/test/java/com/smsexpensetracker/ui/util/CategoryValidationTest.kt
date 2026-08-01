package com.smsexpensetracker.ui.util

import com.smsexpensetracker.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryValidationTest {

    private val existing = listOf(
        Category(id = 1, name = "Food & Dining", icon = "restaurant", color = -13108, isDefault = true),
        Category(id = 2, name = "Groceries", icon = "shopping_cart", color = -13956304, isDefault = true)
    )

    @Test
    fun `blank name is rejected`() {
        assertEquals("Name is required", validateCategoryName("   ", existing, null))
    }

    @Test
    fun `blank name is rejected when editing`() {
        assertEquals("Name is required", validateCategoryName("", existing, 1))
    }

    @Test
    fun `too long name is rejected`() {
        assertEquals(
            "Name must be 30 characters or fewer",
            validateCategoryName("x".repeat(31), existing, null)
        )
    }

    @Test
    fun `duplicate name is rejected case-insensitively`() {
        assertEquals(
            "A category with this name already exists",
            validateCategoryName("food & dining", existing, null)
        )
    }

    @Test
    fun `same name as self when editing is allowed`() {
        assertNull(validateCategoryName("food & dining", existing, 1))
    }

    @Test
    fun `unique name is allowed`() {
        assertNull(validateCategoryName("Coffee", existing, null))
    }
}
