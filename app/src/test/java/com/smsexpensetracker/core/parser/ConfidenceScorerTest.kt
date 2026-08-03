package com.smsexpensetracker.core.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ConfidenceScorerTest(
    private val smsBody: String,
    private val pattern: String,
    private val hasAmount: Boolean,
    private val hasDescription: Boolean,
    private val expectedScore: Float
) {
    companion object {
        @Parameterized.Parameters(name = "{index}: score={4}")
        @JvmStatic
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf(
                "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.",
                "Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
                true, true, 1.0f
            ),
            arrayOf(
                "Random text with no match",
                "Spent Rs\\.([\\d,.]+) On HDFC",
                false, false, 0.0f
            ),
            arrayOf(
                "Has amount but no pattern match",
                "ICICI.*invalid",
                true, false, 0.4f
            ),
            arrayOf(
                "Match without amount value",
                "without",
                false, true, 0.5f
            ),
            arrayOf(
                "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.",
                "Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}",
                true, true, 1.0f
            ),
            arrayOf(
                "Random text with no match",
                "Spent Rs.{amount} On HDFC Bank Card {card}",
                false, false, 0.0f
            )
        )
    }

    @Test
    fun score() {
        val result = ConfidenceScorer.score(smsBody, pattern, hasAmount, hasDescription)
        assertEquals(expectedScore, result.value, 0.001f)

    }
}