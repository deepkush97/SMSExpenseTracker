package com.smsexpensetracker.core.categorize

import com.smsexpensetracker.domain.model.UserCategoryRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoCategoryEngineTest {

    private val shopping = UserCategoryRule(id = 1L, pattern = "amazon", categoryId = 10L)
    private val food = UserCategoryRule(id = 2L, pattern = "zomato", categoryId = 11L)

    @Test
    fun `matches case-insensitive substring`() {
        assertEquals(10L, AutoCategoryEngine.matchCategory("PAYMENT VIA AMAZON IN", listOf(shopping)))
    }

    @Test
    fun `first matching rule wins`() {
        assertEquals(
            10L,
            AutoCategoryEngine.matchCategory("ZOMATO ORDER VIA AMAZON PAY", listOf(shopping, food))
        )
    }

    @Test
    fun `returns null when no rule matches`() {
        assertNull(AutoCategoryEngine.matchCategory("SWIGGY ORDER", listOf(shopping, food)))
    }

    @Test
    fun `returns null for empty rules`() {
        assertNull(AutoCategoryEngine.matchCategory("SWIGGY ORDER", emptyList()))
    }
}
