package com.smsexpensetracker.data.csv

import android.content.ContentResolver
import com.smsexpensetracker.core.csv.CsvCodec
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class CsvImporterTest {

    private val repository = mockk<TransactionRepository>()
    private val resolver = mockk<ContentResolver>()

    private val existing = Transaction(
        id = 1L, bankId = 2L, amount = 500L, transactionType = TransactionType.CREDIT,
        description = "Refund", transactionDate = LocalDateTime.of(2026, 8, 1, 9, 0),
        categoryId = null, rawSms = "SMS", smsTimestamp = 1L,
        createdAt = LocalDateTime.of(2026, 8, 1, 9, 0), parseMethod = ParseMethod.SMS
    )

    private val importer = CsvImporter(resolver, repository)

    private fun csvWith(vararg rows: List<String>): String =
        CsvCodec.encode(listOf(CsvCodec.HEADER) + rows.toList())

    @Test
    fun `importFromText inserts valid rows`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        coEvery { repository.insertBatch(any()) } returns 2
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "A", "2", "", "0", "SMS", "raw1"),
            listOf("2026-08-02T11:00:00", "200", "CREDIT", "B", "3", "", "0", "SMS", "raw2")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 2, skipped = 0, invalid = 0), result)
        coVerifyInserted(2)
    }

    @Test
    fun `importFromText counts invalid rows but inserts valid ones`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        coEvery { repository.insertBatch(any()) } returns 1
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "abc", "DEBIT", "bad amount", "2", "", "0", "SMS", "raw"),
            listOf("2026-08-02T10:00:00", "0", "DEBIT", "zero", "2", "", "0", "SMS", "raw"),
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "good", "2", "", "0", "SMS", "raw")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 1, skipped = 0, invalid = 2), result)
    }

    @Test
    fun `importFromText skips rows matching existing transactions`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(listOf(existing))
        coEvery { repository.insertBatch(any()) } returns 1
        val text = csvWith(
            listOf("2026-08-01T09:00:00", "500", "CREDIT", "Refund", "2", "", "0", "SMS", "SMS"),
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "New", "2", "", "0", "SMS", "raw")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 1, skipped = 1, invalid = 0), result)
        coVerifyInserted(1)
    }

    @Test
    fun `importFromText dedups duplicate rows within file`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        coEvery { repository.insertBatch(any()) } returns 1
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "A", "2", "", "0", "SMS", "raw"),
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "A", "2", "", "0", "SMS", "raw")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 1, skipped = 1, invalid = 0), result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `importFromText throws on malformed csv structure`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        importer.importFromText("\"unterminated")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `importFromText throws on wrong header`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        importer.importFromText("foo,bar\n1,2")
    }

    @Test
    fun `importFrom returns empty result for missing bankId rows`() = runTest {
        every { repository.getAllTransactions() } returns flowOf(emptyList())
        val text = csvWith(
            listOf("2026-08-02T10:00:00", "100", "DEBIT", "no bank", "", "", "0", "SMS", "raw")
        )

        val result = importer.importFromText(text)

        assertEquals(ImportResult(imported = 0, skipped = 0, invalid = 1), result)
    }

    private suspend fun coVerifyInserted(count: Int) {
        io.mockk.coVerify(exactly = 1) {
            repository.insertBatch(match { list -> list.size == count && list.all { it.bankId > 0 } })
        }
    }
}
