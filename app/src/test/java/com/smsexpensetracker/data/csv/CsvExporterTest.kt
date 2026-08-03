package com.smsexpensetracker.data.csv

import android.content.Context
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class CsvExporterTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val repository = mockk<TransactionRepository>()

    private val tx = Transaction(
        id = 1L, bankId = 2L, amount = 100L, transactionType = TransactionType.CREDIT,
        description = "Refund", transactionDate = LocalDateTime.of(2026, 8, 2, 9, 0),
        categoryId = null, rawSms = "SMS", smsTimestamp = 1L,
        createdAt = LocalDateTime.of(2026, 8, 2, 9, 0), parseMethod = ParseMethod.SMS
    )

    @Test
    fun `buildExportFile writes file under exports dir with content`() = runTest {
        coEvery { repository.getAllTransactions() } returns flowOf(listOf(tx))
        val exporter = CsvExporter(mockk<Context>(), tempDir.root, repository)

        val result = exporter.buildExportFile()

        assertTrue(result.file.parentFile?.name == "exports")
        assertTrue(result.file.name.startsWith("transactions_"))
        assertEquals(1, result.count)
        val rows = CsvCodec.parse(result.file.readText())
        assertEquals(CsvCodec.HEADER, rows.first())
        assertEquals("100", rows[1][1])
        assertEquals("CREDIT", rows[1][2])
    }

    @Test
    fun `buildExportFile writes header-only csv for empty data`() = runTest {
        coEvery { repository.getAllTransactions() } returns flowOf(emptyList())
        val exporter = CsvExporter(mockk<Context>(), tempDir.root, repository)

        val result = exporter.buildExportFile()

        assertEquals(0, result.count)
        val rows = CsvCodec.parse(result.file.readText())
        assertEquals(1, rows.size)
        assertEquals(CsvCodec.HEADER, rows.first())
    }

    @Test
    fun `buildExportFile performs work off the test thread`() = runTest {
        var onBackgroundThread = false
        every { repository.getAllTransactions() } answers {
            onBackgroundThread = Thread.currentThread().name != "Test worker"
            flowOf(listOf(tx))
        }
        val exporter = CsvExporter(mockk<Context>(), tempDir.root, repository)

        exporter.buildExportFile()

        assertTrue(onBackgroundThread)
    }
}
