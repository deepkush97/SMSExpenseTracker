package com.smsexpensetracker.core.parser

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SenderDetectorTest(
    private val input: String,
    private val expected: String,
) {
    companion object {
        @Parameterized.Parameters(name = "{0} ->{1}")
        @JvmStatic
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf("AD-HDFCBK-S", "HDFCBK"),
            arrayOf("AD-HDFCBK", "HDFCBK"),
            arrayOf("AD-ICICIB-S", "ICICIB"),
            arrayOf("AD-SBI-S", "SBI"),
            arrayOf("AD-AXISBANK-S", "AXISBANK"),
            arrayOf("AD-BOB-S", "BOB"),
            arrayOf("KKBK", "KKBK"),
        )
    }

    @Test
    fun detectsBankCode() {
        val senderId = SenderDetector.detect(input)
        assertEquals(expected, senderId.value)

    }
}