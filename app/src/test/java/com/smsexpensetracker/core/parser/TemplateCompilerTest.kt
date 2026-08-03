package com.smsexpensetracker.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateCompilerTest {

    private val sms = "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You?"

    @Test
    fun `isTemplate detects placeholder braces`() {
        assertTrue(TemplateCompiler.isTemplate("Spent Rs.{amount} On HDFC Bank Card {card}"))
        assertFalse(TemplateCompiler.isTemplate("Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4}"))
    }

    @Test
    fun `isTemplate does not treat quantifier braces as template`() {
        assertFalse(TemplateCompiler.isTemplate("Spent \\d{4}"))
    }

    @Test
    fun `compile rejects template without amount`() {
        assertNull(TemplateCompiler.compile("Your Card {card} credited"))
    }

    @Test
    fun `compile rejects malformed template`() {
        assertNull(TemplateCompiler.compile("Rs.{amount} On {date"))
    }

    @Test
    fun `extract parses amount and description`() {
        val result = TemplateCompiler.extract(
            sms,
            "Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}",
            1L
        )
        assertEquals(483176L, result?.amount)
        assertEquals("Acme Inc.", result?.description)
        assertEquals(1L, result?.bankId)
    }

    @Test
    fun `extract matches with flexible whitespace`() {
        val result = TemplateCompiler.extract(
            "Rs. 546.00 spent from Pluxee  Meal Card wallet",
            "Rs. {amount} spent from Pluxee Meal Card wallet",
            4L
        )
        assertEquals(54600L, result?.amount)
    }

    @Test
    fun `extract combines repeated descriptions with semicolon`() {
        val result = TemplateCompiler.extract(
            "100.00 debited at Swiggy ref 1234",
            "{amount} debited at {description} ref {description}",
            2L
        )
        assertEquals(10000L, result?.amount)
        assertEquals("Swiggy; 1234", result?.description)
    }

    @Test
    fun `extract terminal placeholder captures to end`() {
        val result = TemplateCompiler.extract(
            "INR 1000.00 deducted from HDFC Bank A/C No 1234 towards Some CORP UMRN",
            "INR {amount} deducted from HDFC Bank A/C No {account} towards {description}",
            1L
        )
        assertEquals(100000L, result?.amount)
        assertEquals("Some CORP UMRN", result?.description)
    }

    @Test
    fun `extract terminal description captures across newlines`() {
        val result = TemplateCompiler.extract(
            "INR 1000.00 deducted from HDFC Bank A/C No 1234 towards Some CORP\nUMRN 123456789",
            "INR {amount} deducted from HDFC Bank A/C No {account} towards {description}",
            1L
        )
        assertEquals(100000L, result?.amount)
        assertEquals("Some CORP\nUMRN 123456789", result?.description)
    }

    @Test
    fun `extract returns null when amount is not parseable`() {
        assertNull(
            TemplateCompiler.extract("Spent abc On HDFC Bank Card", "Spent {amount} On HDFC Bank Card", 1L)
        )
    }

    @Test
    fun `extract uses first amount occurrence`() {
        val result = TemplateCompiler.extract(
            "Rs. 546.00 and Rs. 999.00",
            "Rs. {amount} and Rs. {amount}",
            1L
        )
        assertEquals(54600L, result?.amount)
    }

    @Test
    fun `findPlaceholders returns names in order`() {
        assertEquals(
            listOf("amount", "card", "description"),
            TemplateCompiler.findPlaceholders("Rs.{amount} Card {card} At {description}")
        )
    }
}
