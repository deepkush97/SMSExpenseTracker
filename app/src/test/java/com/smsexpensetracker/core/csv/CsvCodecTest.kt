package com.smsexpensetracker.core.csv

import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CsvCodecTest {

    private val tx = Transaction(
        id = 1L,
        bankId = 2L,
        amount = 125050L,
        transactionType = TransactionType.DEBIT,
        description = "Acme Inc",
        transactionDate = LocalDateTime.of(2026, 8, 2, 10, 0, 0),
        categoryId = 3L,
        rawSms = "Spent Rs.1,250.50 at Acme On 02-08-26",
        smsTimestamp = 1750000000000L,
        createdAt = LocalDateTime.of(2026, 8, 2, 10, 0, 0),
        parseMethod = ParseMethod.SMS
    )

    @Test
    fun `encode and parse round-trip plain fields`() {
        val csv = CsvCodec.encode(listOf(listOf("a", "b"), listOf("1", "2")))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), CsvCodec.parse(csv))
    }

    @Test
    fun `encode quotes fields with comma quote newline`() {
        val csv = CsvCodec.encode(listOf(listOf("a,b", "say \"hi\"", "line1\nline2")))
        assertEquals("\"a,b\",\"say \"\"hi\"\"\",\"line1\nline2\"", csv)
    }

    @Test
    fun `parse quoted fields with embedded comma and quote`() {
        val rows = CsvCodec.parse("\"a,b\",\"say \"\"hi\"\"\"")
        assertEquals(listOf(listOf("a,b", "say \"hi\"")), rows)
    }

    @Test
    fun `parse handles crlf line endings`() {
        val rows = CsvCodec.parse("a,b\r\nc,d\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), rows)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on unterminated quote`() {
        CsvCodec.parse("\"unterminated")
    }

    @Test
    fun `toCsv uses paisa amount and iso date with header`() {
        val csv = CsvCodec.toCsv(listOf(tx))
        val rows = CsvCodec.parse(csv)
        assertEquals(
            listOf(
                "date", "amount", "type", "description", "bankId", "categoryId",
                "smsTimestamp", "parseMethod", "rawSms"
            ),
            rows.first()
        )
        assertEquals(
            listOf(
                "2026-08-02T10:00", "125050", "DEBIT", "Acme Inc", "2", "3",
                "1750000000000", "SMS", tx.rawSms
            ),
            rows[1]
        )
    }

    @Test
    fun `fromCsv round-trips transaction fields`() {
        val roundTripped = CsvCodec.fromCsv(CsvCodec.toCsv(listOf(tx))).single()
        assertEquals(125050L, roundTripped.amount)
        assertEquals(TransactionType.DEBIT, roundTripped.type)
        assertEquals("Acme Inc", roundTripped.description)
        assertEquals(LocalDateTime.of(2026, 8, 2, 10, 0, 0), roundTripped.transactionDate)
        assertEquals(2L, roundTripped.bankId)
        assertEquals(3L, roundTripped.categoryId)
        assertEquals(1750000000000L, roundTripped.smsTimestamp)
        assertEquals(ParseMethod.SMS, roundTripped.parseMethod)
        assertEquals(tx.rawSms, roundTripped.rawSms)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromCsv throws on wrong header`() {
        CsvCodec.fromCsv("foo,bar\n1,2")
    }

    @Test
    fun `fromCsv defaults missing optional fields`() {
        val csv = CsvCodec.encode(
            listOf(
                CsvCodec.HEADER,
                listOf("2026-08-02T10:00:00", "500", "CREDIT", "Refund", "", "", "", "SMS", "")
            )
        )
        val row = CsvCodec.fromCsv(csv).single()
        assertNull(row.bankId)
        assertNull(row.categoryId)
        assertEquals(0L, row.smsTimestamp)
        assertEquals("", row.rawSms)
    }

    @Test
    fun `toTransactionRow returns null for unparseable amount`() {
        assertNull(CsvCodec.toTransactionRow(listOf("2026-08-02T10:00:00", "abc", "DEBIT", "x", "1", "", "0", "SMS", "")))
    }

    @Test
    fun `toTransactionRow returns null for unknown enum`() {
        assertNull(CsvCodec.toTransactionRow(listOf("2026-08-02T10:00:00", "500", "NOPE", "x", "1", "", "0", "SMS", "")))
    }

    @Test
    fun `empty transactions produce header-only csv`() {
        val rows = CsvCodec.parse(CsvCodec.toCsv(emptyList()))
        assertEquals(1, rows.size)
        assertEquals(CsvCodec.HEADER, rows.first())
    }
}
