package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.entity.TransactionType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DemoTransactionGeneratorTest {

    private val transactions = DemoTransactionGenerator.generate()

    @Test
    fun `generates at least 60 rows`() {
        assertTrue(transactions.size >= 60)
    }

    @Test
    fun `covers all seeded banks`() {
        assertTrue(transactions.map { it.bankId }.toSet().containsAll((1L..5L).toSet()))
    }

    @Test
    fun `covers all seeded categories`() {
        assertTrue(transactions.mapNotNull { it.categoryId }.toSet().containsAll((1L..14L).toSet()))
    }

    @Test
    fun `amounts are positive paisa`() {
        assertTrue(transactions.all { it.amount > 0 })
    }

    @Test
    fun `contains both credit and debit`() {
        assertTrue(transactions.any { it.type == TransactionType.CREDIT })
        assertTrue(transactions.any { it.type == TransactionType.DEBIT })
    }

    @Test
    fun `dates fall within the last 3 months`() {
        val cutoff = LocalDateTime.now().minusMonths(3)
        assertTrue(transactions.all { it.transactionDate.isAfter(cutoff) })
    }

    @Test
    fun `rows are distinct`() {
        assertTrue(transactions.size == transactions.toSet().size)
    }
}
