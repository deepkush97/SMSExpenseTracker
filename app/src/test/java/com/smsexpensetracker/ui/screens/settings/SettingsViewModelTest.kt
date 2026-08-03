package com.smsexpensetracker.ui.screens.settings

import android.net.Uri
import com.smsexpensetracker.core.settings.ThemePreferences
import com.smsexpensetracker.data.csv.ExportResult
import com.smsexpensetracker.data.csv.ImportResult
import com.smsexpensetracker.domain.usecase.ExportCsvUseCase
import com.smsexpensetracker.domain.usecase.ImportCsvUseCase
import com.smsexpensetracker.ui.theme.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val themePreferences = mockk<ThemePreferences>()
    private val exportCsvUseCase = mockk<ExportCsvUseCase>()
    private val importCsvUseCase = mockk<ImportCsvUseCase>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(themePreferences, exportCsvUseCase, importCsvUseCase)

    @Test
    fun `exposes persisted theme mode`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.DARK)
        val viewModel = viewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        job.cancel()
    }

    @Test
    fun `change persists the selected mode`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { themePreferences.setThemeMode(any()) } returns Unit
        val viewModel = viewModel()
        viewModel.onThemeModeChange(ThemeMode.AMOLED)
        advanceUntilIdle()
        coVerify { themePreferences.setThemeMode(ThemeMode.AMOLED) }
    }

    @Test
    fun `exportCsv sets pendingExport on success`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        val result = ExportResult(mockk<Uri>(), "transactions_x.csv", 3)
        coEvery { exportCsvUseCase() } returns Result.success(result)
        val viewModel = viewModel()

        viewModel.exportCsv()
        advanceUntilIdle()

        assertEquals(result, viewModel.uiState.value.pendingExport)
        assertEquals("Exported 3 transactions", viewModel.uiState.value.csvMessage)
        assertFalse(viewModel.uiState.value.isCsvBusy)
    }

    @Test
    fun `exportCsv sets error message on failure`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { exportCsvUseCase() } returns Result.failure(RuntimeException("disk"))
        val viewModel = viewModel()

        viewModel.exportCsv()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingExport)
        assertTrue(viewModel.uiState.value.csvMessage?.contains("Export failed") == true)
    }

    @Test
    fun `importCsv sets success message`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { importCsvUseCase(any()) } returns Result.success(ImportResult(5, 1, 2))
        val viewModel = viewModel()

        viewModel.importCsv(mockk<Uri>())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.csvMessage?.contains("Imported 5") == true)
        assertFalse(viewModel.uiState.value.isCsvBusy)
    }

    @Test
    fun `importCsv sets error message on failure`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { importCsvUseCase(any()) } returns Result.failure(IllegalArgumentException("bad header"))
        val viewModel = viewModel()

        viewModel.importCsv(mockk<Uri>())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.csvMessage?.contains("Import failed") == true)
    }

    @Test
    fun `isCsvBusy gates concurrent export calls`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { exportCsvUseCase() } returns Result.success(ExportResult(mockk<Uri>(), "a.csv", 1))
        val viewModel = viewModel()

        viewModel.exportCsv()
        viewModel.exportCsv()
        advanceUntilIdle()

        coVerify(exactly = 1) { exportCsvUseCase() }
    }

    @Test
    fun `consumeCsvMessage clears the message`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { exportCsvUseCase() } returns Result.failure(RuntimeException("x"))
        val viewModel = viewModel()
        viewModel.exportCsv()
        advanceUntilIdle()

        viewModel.consumeCsvMessage()

        assertNull(viewModel.uiState.value.csvMessage)
    }
}
