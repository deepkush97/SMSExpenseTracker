package com.smsexpensetracker.ui.screens.settings

import android.net.Uri
import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.core.settings.ThemePreferences
import com.smsexpensetracker.data.csv.ExportResult
import com.smsexpensetracker.data.demo.DemoDataSeeder
import com.smsexpensetracker.data.csv.ImportResult
import com.smsexpensetracker.domain.model.SyncMeta
import com.smsexpensetracker.domain.repository.SyncMetaRepository
import com.smsexpensetracker.domain.usecase.ExportCsvUseCase
import com.smsexpensetracker.domain.usecase.ImportCsvUseCase
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import com.smsexpensetracker.domain.value.SyncRange
import com.smsexpensetracker.domain.value.SyncResult
import com.smsexpensetracker.ui.theme.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val demoDataSeeder = mockk<DemoDataSeeder>()
    private val demoDataPreferences = mockk<DemoDataPreferences>()
    private val smsSyncUseCase = mockk<SmsSyncUseCase>()
    private val syncMetaRepository = mockk<SyncMetaRepository>()
    private val demoDataLoadedFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { demoDataPreferences.demoDataLoaded } returns demoDataLoadedFlow
        coEvery { syncMetaRepository.get() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        SettingsViewModel(themePreferences, exportCsvUseCase, importCsvUseCase, demoDataSeeder, demoDataPreferences, smsSyncUseCase, syncMetaRepository)

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

    @Test
    fun `loadDemoData shows loaded message when seeder inserts`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { demoDataSeeder.seedIfEmpty() } returns 60
        val viewModel = viewModel()

        viewModel.loadDemoData()
        advanceUntilIdle()

        assertEquals("Loaded 60 demo transactions", viewModel.uiState.value.demoMessage)
        assertFalse(viewModel.uiState.value.isDemoBusy)
    }

    @Test
    fun `loadDemoData shows already-loaded message when seeder skips`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { demoDataSeeder.seedIfEmpty() } returns 0
        val viewModel = viewModel()

        viewModel.loadDemoData()
        advanceUntilIdle()

        assertEquals("Demo data already loaded", viewModel.uiState.value.demoMessage)
        assertFalse(viewModel.uiState.value.isDemoBusy)
    }

    @Test
    fun `consumeDemoMessage clears the message`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { demoDataSeeder.seedIfEmpty() } returns 60
        val viewModel = viewModel()
        viewModel.loadDemoData()
        advanceUntilIdle()

        viewModel.consumeDemoMessage()

        assertNull(viewModel.uiState.value.demoMessage)
    }

    @Test
    fun `loadDemoData surfaces a failure message`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { demoDataSeeder.seedIfEmpty() } throws RuntimeException("disk full")
        val viewModel = viewModel()

        viewModel.loadDemoData()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.demoMessage?.contains("Demo load failed") == true)
        assertFalse(viewModel.uiState.value.isDemoBusy)
    }

    @Test
    fun `importCsv opens demo barrier instead of importing when demo data present`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        demoDataLoadedFlow.value = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.importCsv(mockk<Uri>())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showDemoBarrier)
        coVerify(exactly = 0) { importCsvUseCase(any()) }
    }

    @Test
    fun `requestDeleteDemo opens the barrier`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        demoDataLoadedFlow.value = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.requestDeleteDemo()

        assertTrue(viewModel.uiState.value.showDemoBarrier)
    }

    @Test
    fun `confirmDeleteDemoData deletes demo, closes barrier, sets message`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        demoDataLoadedFlow.value = true
        coEvery { demoDataSeeder.deleteDemoData() } returns Unit
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.confirmDeleteDemoData()
        advanceUntilIdle()

        coVerify(exactly = 1) { demoDataSeeder.deleteDemoData() }
        assertFalse(viewModel.uiState.value.showDemoBarrier)
        assertEquals("Demo data deleted", viewModel.uiState.value.demoMessage)
    }

    @Test
    fun `loads last sync time from repo`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { syncMetaRepository.get() } returns SyncMeta(lastSyncTimestamp = 1750000000000L, lastSmsId = null)
        val viewModel = viewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1750000000000L, viewModel.uiState.value.lastSyncTime)
        job.cancel()
    }

    @Test
    fun `resync triggers use case with selected range`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { syncMetaRepository.get() } returns SyncMeta(lastSyncTimestamp = 0L, lastSmsId = null)
        coEvery { smsSyncUseCase.sync(any()) } returns SyncResult(scanned = 3, inserted = 1, unparsed = 0)
        val viewModel = viewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onSyncRangeChange(SyncRange.LAST_1W)
        viewModel.resync()
        advanceUntilIdle()

        coVerify { smsSyncUseCase.sync(SyncRange.LAST_1W) }
        assertTrue(!viewModel.uiState.value.isSyncing)
        assertTrue(viewModel.uiState.value.syncMessage!!.contains("Scanned 3"))
        job.cancel()
    }

    @Test
    fun `resync blocks while already syncing`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { syncMetaRepository.get() } returns SyncMeta(lastSyncTimestamp = 0L, lastSmsId = null)
        coEvery { smsSyncUseCase.sync(any()) } returns SyncResult()
        val viewModel = viewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.resync()
        viewModel.resync()
        advanceUntilIdle()

        coVerify(exactly = 1) { smsSyncUseCase.sync(any()) }
        job.cancel()
    }
}
