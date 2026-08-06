package com.smsexpensetracker.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultilineWhitespaceTest {

    private val hdfc = listOf(
        1L to "Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}",
        1L to "Txn Rs.{amount} On HDFC Bank Card {card} At {description} by UPI {upi} On {date}",
    )
    private val icici = listOf(
        2L to "ICICI Bank Acct {account} debited for Rs {amount} on {date}; {description} credited. UPI",
    )

    @Test
    fun `line break inside literal amount prefix still parses`() {
        val result = ParserEngine.parse(
            "Spent Rs.\n4831.76 On HDFC Bank Card 1111\nAt Acme Inc. On 2026-07-26:21:35:51",
            "AD-HDFCBK-S", hdfc
        )
        assertEquals(483176L, result.amount)
        assertEquals("Acme Inc.", result.description)
    }

    @Test
    fun `crlf line endings parse`() {
        val result = ParserEngine.parse(
            "Spent Rs.\r\n4831.76 On HDFC Bank Card 1111\r\nAt Acme Inc. On 2026-07-26:21:35:51",
            "AD-HDFCBK-S", hdfc
        )
        assertEquals(483176L, result.amount)
        assertEquals("Acme Inc.", result.description)
    }

    @Test
    fun `tabs and multiple spaces parse`() {
        val result = ParserEngine.parse(
            "Spent\tRs. 4831.76 On   HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51",
            "AD-HDFCBK-S", hdfc
        )
        assertEquals(483176L, result.amount)
        assertEquals("Acme Inc.", result.description)
    }

    @Test
    fun `line break between amount and merchant parses`() {
        val result = ParserEngine.parse(
            "Txn Rs.25.00\nOn HDFC Bank Card 1111\nAt\nQ123456789@ybl\nby UPI 620436716168\nOn 23-07",
            "AD-HDFCBK-S", hdfc
        )
        assertEquals(2500L, result.amount)
        assertEquals("Q123456789@ybl", result.description)
    }

    @Test
    fun `legacy regex parses across line breaks`() {
        val result = ParserEngine.parse(
            "ICICI Bank Acct XX123 debited for\nRs 242.00 on 26-Jul-26;\nBUS Ticket credited. UPI:003637672623.",
            "AD-ICICIT-S",
            listOf(2L to "ICICI Bank Acct \\w+ debited for Rs ([\\d,.]+) on [\\w-]+; (.+?) credited\\. UPI")
        )
        assertEquals(24200L, result.amount)
        assertEquals("BUS Ticket", result.description)
    }

    @Test
    fun `raw sms is preserved unchanged`() {
        val raw = "Spent Rs.\n4831.76 On HDFC Bank Card 1111\nAt Acme Inc. On 2026-07-26:21:35:51"
        val result = ParserEngine.parse(raw, "AD-HDFCBK-S", hdfc)
        assertEquals(raw, result.rawSms)
        assertTrue(raw.contains('\n'))
    }

    @Test
    fun `description no longer contains newlines`() {
        val result = ParserEngine.parse(
            "Txn Rs.25.00\nOn HDFC Bank Card 1111\nAt Q123456789@ybl\nby UPI 620436716168\nOn 23-07",
            "AD-HDFCBK-S", hdfc
        )
        assertEquals("Q123456789@ybl", result.description)
        assertTrue(!result.description.contains('\n'))
    }
}
