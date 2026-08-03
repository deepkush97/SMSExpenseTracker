package com.smsexpensetracker.domain.usecase

import android.net.Uri
import com.smsexpensetracker.data.csv.CsvImporter
import com.smsexpensetracker.data.csv.ImportResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportCsvUseCaseTest {

    private val importer = mockk<CsvImporter>()
    private val useCase = ImportCsvUseCase(importer)
    private val uri = mockk<Uri>()

    @Test
    fun `invoke returns success with import result`() = runTest {
        val result = ImportResult(imported = 5, skipped = 1, invalid = 2)
        coEvery { importer.importFrom(uri) } returns result

        val outcome = useCase(uri)

        assertEquals(result, outcome.getOrThrow())
    }

    @Test
    fun `invoke returns failure when importer throws`() = runTest {
        coEvery { importer.importFrom(uri) } throws IllegalArgumentException("bad header")

        val outcome = useCase(uri)

        assertTrue(outcome.isFailure)
    }

    @Test(expected = CancellationException::class)
    fun `invoke rethrows cancellation`() = runTest {
        coEvery { importer.importFrom(uri) } throws CancellationException("cancel")

        useCase(uri)
    }
}
