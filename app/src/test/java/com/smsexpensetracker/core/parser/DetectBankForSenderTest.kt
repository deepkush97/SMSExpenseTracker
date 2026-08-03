package com.smsexpensetracker.core.parser

import com.smsexpensetracker.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectBankForSenderTest {

    private val hdfc = Bank(id = 1L, name = "HDFC Bank", smsSender = "HDFCBK")
    private val icici = Bank(id = 2L, name = "ICICI Bank", smsSender = "ICICIB")
    private val banks = listOf(hdfc, icici)

    @Test
    fun `exact sender match returns bank id`() {
        assertEquals(1L, detectBankForSender("HDFCBK", banks))
    }

    @Test
    fun `TRAI prefixed sender resolves to bank`() {
        assertEquals(1L, detectBankForSender("AD-HDFCBK-S", banks))
        assertEquals(2L, detectBankForSender("AD-ICICIB-S", banks))
    }

    @Test
    fun `sender containing bank code matches`() {
        assertEquals(1L, detectBankForSender("XXHDFCBKXX", banks))
    }

    @Test
    fun `bank code contained in longer sender matches`() {
        assertEquals(1L, detectBankForSender("HDFCBK-EXTRA", banks))
    }

    @Test
    fun `unknown sender returns null`() {
        assertNull(detectBankForSender("UNKNOWN", banks))
    }

    @Test
    fun `blank sender returns null`() {
        assertNull(detectBankForSender("   ", banks))
    }
}
