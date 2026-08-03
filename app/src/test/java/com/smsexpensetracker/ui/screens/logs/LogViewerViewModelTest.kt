package com.smsexpensetracker.ui.screens.logs

import android.content.Context
import com.smsexpensetracker.data.logging.FileLogger
import com.smsexpensetracker.data.logging.LogFile
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.repository.ParseLogRepository
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val tempDir = TemporaryFolder()

    private val parseLogRepository = mockk<ParseLogRepository>()
    private lateinit var fileLogger: FileLogger

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fileLogger = FileLogger(mockk<Context>(), tempDir.root, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exposes parse logs flow`() = runTest(testDispatcher) {
        val log = ParseLog(1L, "body", "HDFCBK", LocalDateTime.of(2026, 8, 2, 9, 0), ParseStatus.FAILED, "err")
        every { parseLogRepository.getAllLogs() } returns flowOf(listOf(log))
        val viewModel = LogViewerViewModel(parseLogRepository, fileLogger)
        val job = launch { viewModel.parseLogs.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(log), viewModel.parseLogs.value)
        job.cancel()
    }

    @Test
    fun `refresh populates fileLogs`() = runTest(testDispatcher) {
        fileLogger.append(LogFile.ERROR_LOG, "line one")
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        val viewModel = LogViewerViewModel(parseLogRepository, fileLogger)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(4, viewModel.fileLogs.value.size)
        assertEquals(true, viewModel.fileLogs.value.getValue(LogFile.ERROR_LOG).contains("line one"))
    }

    @Test
    fun `clearFile empties the file and refreshes`() = runTest(testDispatcher) {
        fileLogger.append(LogFile.ERROR_LOG, "to be cleared")
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        val viewModel = LogViewerViewModel(parseLogRepository, fileLogger)
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.clearFile(LogFile.ERROR_LOG)
        advanceUntilIdle()

        assertEquals("", viewModel.fileLogs.value.getValue(LogFile.ERROR_LOG))
    }
}
