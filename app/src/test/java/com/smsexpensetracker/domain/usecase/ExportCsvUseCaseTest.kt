package com.smsexpensetracker.domain.usecase

import android.net.Uri
import com.smsexpensetracker.data.csv.CsvExporter
import com.smsexpensetracker.data.csv.ExportResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportCsvUseCaseTest {

    private val exporter = mockk<CsvExporter>()
    private val useCase = ExportCsvUseCase(exporter)

    @Test
    fun `invoke returns success with export result`() = runTest {
        val result = ExportResult(mockk<Uri>(), "transactions_x.csv", 3)
        coEvery { exporter.exportAll() } returns result

        val outcome = useCase()

        assertEquals(result, outcome.getOrThrow())
    }

    @Test
    fun `invoke returns failure when exporter throws`() = runTest {
        coEvery { exporter.exportAll() } throws RuntimeException("disk full")

        val outcome = useCase()

        assertTrue(outcome.isFailure)
    }

    @Test(expected = CancellationException::class)
    fun `invoke rethrows cancellation`() = runTest {
        coEvery { exporter.exportAll() } throws CancellationException("cancel")

        useCase()

        coVerify(exactly = 1) { exporter.exportAll() }
    }
}
