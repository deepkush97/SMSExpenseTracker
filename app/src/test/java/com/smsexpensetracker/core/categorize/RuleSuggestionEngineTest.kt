package com.smsexpensetracker.core.categorize

import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class RuleSuggestionEngineTest {

    private fun tx(id: Long, description: String, categoryId: Long?): Transaction =
        Transaction(
            id = id, bankId = 1L, amount = 100L,
            transactionType = TransactionType.DEBIT,
            description = description,
            transactionDate = LocalDateTime.of(2026, 8, 1, 10, 0),
            categoryId = categoryId, rawSms = "", smsTimestamp = 0L,
            createdAt = LocalDateTime.of(2026, 8, 1, 10, 0)
        )

    @Test
    fun `groups by shared keyword and guesses category by majority`() {
        val uncategorized = listOf(
            tx(1, "PAYMENT VIA AMAZON IN", null),
            tx(2, "AMAZON ORDER CONFIRMED", null),
            tx(3, "amazon grocery", null)
        )
        val classified = listOf(
            tx(10, "AMAZON Prime", 10L),
            tx(11, "AMAZON delivery", 10L)
        )
        val suggestions = RuleSuggestionEngine.suggest(uncategorized, classified, minCount = 2)
        assertEquals(1, suggestions.size)
        val s = suggestions.first()
        assertEquals("amazon", s.keyword)
        assertEquals(5, s.transactionCount)
        assertEquals(10L, s.suggestedCategoryId)
    }

    @Test
    fun `returns null category when no evidence or tie`() {
        val uncategorized = listOf(tx(1, "ZOMATO ORDER VIA", null), tx(2, "ZOMATO EATS", null))
        val suggestions = RuleSuggestionEngine.suggest(uncategorized, emptyList(), minCount = 2)
        assertEquals(1, suggestions.size)
        assertNull(suggestions.first().suggestedCategoryId)
    }

    @Test
    fun `respects minCount threshold`() {
        val uncategorized = listOf(tx(1, "UNIQUE token", null))
        val suggestions = RuleSuggestionEngine.suggest(uncategorized, emptyList(), minCount = 3)
        assertEquals(0, suggestions.size)
    }

    @Test
    fun `drops tokens below min keyword length and punctuation`() {
        val uncategorized = listOf(tx(1, "Uber 12! star", null), tx(2, "UBER reserve", null))
        val suggestions = RuleSuggestionEngine.suggest(uncategorized, emptyList(), minCount = 2)
        assertEquals(1, suggestions.size)
        assertEquals("uber", suggestions.first().keyword)
    }
}