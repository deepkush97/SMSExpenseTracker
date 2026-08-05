package com.smsexpensetracker.ui.screens.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionEditValidationTest {

    @Test
    fun `blank amount is rejected`() {
        assertEquals("Amount is required", validateTransactionEdit("  ", "Food").amount)
    }

    @Test
    fun `unparseable amount is rejected`() {
        assertEquals("Enter a valid amount", validateTransactionEdit("1.2.3", "Food").amount)
    }

    @Test
    fun `zero amount is rejected`() {
        assertEquals("Amount must be greater than zero", validateTransactionEdit("0", "Food").amount)
    }

    @Test
    fun `negative amount is rejected as invalid`() {
        assertEquals("Enter a valid amount", validateTransactionEdit("-5", "Food").amount)
    }

    @Test
    fun `blank description is rejected`() {
        assertEquals("Description is required", validateTransactionEdit("100", "   ").description)
    }

    @Test
    fun `overlong description is rejected`() {
        assertEquals(
            "Description must be 200 characters or fewer",
            validateTransactionEdit("100", "x".repeat(201)).description
        )
    }

    @Test
    fun `valid input has no errors`() {
        val errors = validateTransactionEdit("1,250.50", "Swiggy order")
        assertNull(errors.amount)
        assertNull(errors.description)
    }
}
