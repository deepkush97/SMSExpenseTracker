package com.smsexpensetracker.ui.util

import com.smsexpensetracker.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankRulesValidationTest {

    private val existing = listOf(
        Bank(id = 1, name = "HDFC Bank", smsSender = "HDFCBK"),
        Bank(id = 2, name = "ICICI Bank", smsSender = "ICICIB")
    )

    @Test
    fun `bank name blank is rejected`() {
        assertEquals("Name is required", validateBankName("  ", existing, null))
    }

    @Test
    fun `bank name too long is rejected`() {
        assertEquals(
            "Name must be 30 characters or fewer",
            validateBankName("x".repeat(31), existing, null)
        )
    }

    @Test
    fun `bank name duplicate is rejected case-insensitively`() {
        assertEquals(
            "A bank with this name already exists",
            validateBankName("hdfc bank", existing, null)
        )
    }

    @Test
    fun `bank name same as self when editing is allowed`() {
        assertNull(validateBankName("hdfc bank", existing, 1))
    }

    @Test
    fun `bank name unique is allowed`() {
        assertNull(validateBankName("Axis Bank", existing, null))
    }

    @Test
    fun `bank sender blank is rejected`() {
        assertEquals("Sender is required", validateBankSender("   "))
    }

    @Test
    fun `bank sender non-blank is allowed`() {
        assertNull(validateBankSender("AXISB"))
    }

    @Test
    fun `rule description blank is rejected`() {
        assertEquals("Description is required", validateRuleDescription(""))
    }

    @Test
    fun `rule description too long is rejected`() {
        assertEquals(
            "Description must be 60 characters or fewer",
            validateRuleDescription("x".repeat(61))
        )
    }

    @Test
    fun `rule description valid is allowed`() {
        assertNull(validateRuleDescription("HDFC UPI Credit"))
    }

    @Test
    fun `pattern blank is rejected`() {
        assertEquals("Pattern is required", validatePattern("  "))
    }

    @Test
    fun `pattern invalid regex is rejected`() {
        assertEquals(
            "Pattern must be a valid regular expression",
            validatePattern("Spent Rs\\.([\\d,.]+")
        )
    }

    @Test
    fun `pattern valid regex is allowed`() {
        assertNull(validatePattern("Spent Rs\\.([\\d,.]+) On HDFC Bank Card"))
    }
}
