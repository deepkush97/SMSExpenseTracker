package com.smsexpensetracker.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AmountFormatterTest(
    private val paisa: Long,
    private val expected: String
) {
    @Test
    fun formatsWithIndianGrouping() {
        assertEquals(expected, formatPaisa(paisa))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} paisa -> {1}")
        fun data(): List<Array<Any>> = listOf(
            arrayOf(0L, "₹0.00"),
            arrayOf(5L, "₹0.05"),
            arrayOf(100L, "₹1.00"),
            arrayOf(50L, "₹0.50"),
            arrayOf(123456789L, "₹12,34,567.89"),
            arrayOf(1234567890L, "₹1,23,45,678.90"),
            arrayOf(100000000L, "₹10,00,000.00"),
            arrayOf(-123456789L, "₹-12,34,567.89"),
            arrayOf(-5L, "₹-0.05")
        )
    }
}

class AmountFormatterSignTest {

    @Test
    fun creditAddsPlusSign() {
        assertEquals("+₹12,34,567.89", formatAmountWithSign(123456789L))
    }

    @Test
    fun debitAddsMinusSign() {
        assertEquals("-₹12,34,567.89", formatAmountWithSign(-123456789L))
    }

    @Test
    fun zeroRendersAsPlus() {
        assertEquals("+₹0.00", formatAmountWithSign(0L))
    }
}

class AmountFormatterInputTest {

    @Test
    fun wholeRupeesKeepTrailingZeros() {
        assertEquals("100.00", formatPaisaInput(10000L))
    }

    @Test
    fun paisePadToTwoDigits() {
        assertEquals("1234.50", formatPaisaInput(123450L))
    }

    @Test
    fun singlePaiseRendersWithLeadingZero() {
        assertEquals("0.05", formatPaisaInput(5L))
    }
}
