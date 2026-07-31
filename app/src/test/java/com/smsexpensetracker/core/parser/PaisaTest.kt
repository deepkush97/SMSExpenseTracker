package com.smsexpensetracker.core.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PaisaTest(
    private val input: String,
    private val expected: Long?
) {
    @Test
    fun parses() {
        assertEquals(expected, parsePaisa(input))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun data(): List<Array<Any?>> = listOf(
            arrayOf("100.50", 10050L),
            arrayOf("1,250.50", 125050L),
            arrayOf("45", 4500L),
            arrayOf("0.29", 29L),
            arrayOf("0", 0L),
            arrayOf("", null),
            arrayOf("abc", null),
            arrayOf("1.234", null),
            arrayOf("-10", null)
        )
    }
}
