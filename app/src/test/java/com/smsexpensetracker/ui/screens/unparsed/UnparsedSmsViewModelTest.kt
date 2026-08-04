package com.smsexpensetracker.ui.screens.unparsed

import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.ParseLogRepository
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import com.smsexpensetracker.domain.value.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class UnparsedSmsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val parseLogRepository = mockk<ParseLogRepository>()
    private val bankRepository = mockk<BankRepository>()
    private val smsSyncUseCase = mockk<SmsSyncUseCase>()

    private val hdfc = Bank(id = 1L, name = "HDFC Bank", smsSender = "HDFCBK")
    private val banks = listOf(hdfc)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun failedLog(
        body: String = "body",
        sender: String = "HDFCBK",
        at: LocalDateTime = LocalDateTime.of(2026, 8, 3, 10, 0),
        error: String? = "no match"
    ) = ParseLog(0L, body, sender, at, ParseStatus.FAILED, error)

    private fun viewModel() =
        UnparsedSmsViewModel(parseLogRepository, bankRepository, smsSyncUseCase)

    @Test
    fun `failedLogs dedupes by body with count and newest time`() = runTest(testDispatcher) {
        val t1 = LocalDateTime.of(2026, 8, 3, 10, 0)
        val t2 = LocalDateTime.of(2026, 8, 3, 11, 0)
        every { parseLogRepository.getAllLogs() } returns flowOf(
            listOf(failedLog(at = t1), failedLog(at = t2), failedLog(body = "other", at = t2))
        )
        every { bankRepository.getAllBanks() } returns flowOf(banks)
        val vm = viewModel()
        val job = launch { vm.failedLogs.collect {} }
        advanceUntilIdle()
        assertEquals(2, vm.failedLogs.value.size)
        val first = vm.failedLogs.value.first()
        assertEquals("body", first.smsBody)
        assertEquals(2, first.failCount)
        assertEquals(t2, first.lastParsedAt)
        job.cancel()
    }

    @Test
    fun `failedLogs excludes non-failed logs`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(
            listOf(
                failedLog(),
                ParseLog(1L, "ok", "HDFCBK", LocalDateTime.of(2026, 8, 3, 9, 0), ParseStatus.SUCCESS, null)
            )
        )
        every { bankRepository.getAllBanks() } returns flowOf(banks)
        val vm = viewModel()
        val job = launch { vm.failedLogs.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.failedLogs.value.size)
        assertEquals("body", vm.failedLogs.value.single().smsBody)
        job.cancel()
    }

    @Test
    fun `failedLogs detects bank from sender`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(listOf(failedLog()))
        every { bankRepository.getAllBanks() } returns flowOf(banks)
        val vm = viewModel()
        val job = launch { vm.failedLogs.collect {} }
        advanceUntilIdle()
        assertEquals(1L, vm.failedLogs.value.single().bankId)
        job.cancel()
    }

    @Test
    fun `failedLogs leaves bankId null for unknown sender`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(listOf(failedLog(sender = "UNKNOWN")))
        every { bankRepository.getAllBanks() } returns flowOf(banks)
        val vm = viewModel()
        val job = launch { vm.failedLogs.collect {} }
        advanceUntilIdle()
        assertNull(vm.failedLogs.value.single().bankId)
        job.cancel()
    }

    @Test
    fun `default filter is FAILED`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        val vm = viewModel()
        assertEquals(UnparsedFilter.FAILED, vm.uiState.value.filter)
    }

    @Test
    fun `setFilter updates the filter`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        val vm = viewModel()
        vm.setFilter(UnparsedFilter.ALL)
        assertEquals(UnparsedFilter.ALL, vm.uiState.value.filter)
    }

    @Test
    fun `resync deletes failed logs then runs sync and reports result`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        coEvery { parseLogRepository.deleteFailed() } returns Unit
        coEvery { smsSyncUseCase.sync() } returns SyncResult(scanned = 3, inserted = 1, unparsed = 2)
        val vm = viewModel()

        vm.resync()
        advanceUntilIdle()

        coVerifyOrder {
            parseLogRepository.deleteFailed()
            smsSyncUseCase.sync()
        }
        assertEquals("Scanned 3, added 1, unparsed 2", vm.uiState.value.syncMessage)
        assertFalse(vm.uiState.value.isSyncing)
    }

    @Test
    fun `resync with sync error reports failure message`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        coEvery { parseLogRepository.deleteFailed() } returns Unit
        coEvery { smsSyncUseCase.sync() } returns SyncResult(error = "boom")
        val vm = viewModel()

        vm.resync()
        advanceUntilIdle()

        assertEquals("Sync failed. Try again.", vm.uiState.value.syncMessage)
        assertFalse(vm.uiState.value.isSyncing)
    }

    @Test
    fun `resync with thrown exception reports failure message`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        coEvery { parseLogRepository.deleteFailed() } throws RuntimeException("db down")
        val vm = viewModel()

        vm.resync()
        advanceUntilIdle()

        assertEquals("Sync failed. Try again.", vm.uiState.value.syncMessage)
        assertFalse(vm.uiState.value.isSyncing)
    }

    @Test
    fun `resync is gated while a sync is in flight`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        coEvery { parseLogRepository.deleteFailed() } returns Unit
        coEvery { smsSyncUseCase.sync() } returns SyncResult()
        val vm = viewModel()

        vm.resync()
        vm.resync()
        advanceUntilIdle()

        coVerify(exactly = 1) { smsSyncUseCase.sync() }
    }

    @Test
    fun `consumeSyncMessage clears the message`() = runTest(testDispatcher) {
        every { parseLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { bankRepository.getAllBanks() } returns flowOf(emptyList())
        coEvery { parseLogRepository.deleteFailed() } returns Unit
        coEvery { smsSyncUseCase.sync() } returns SyncResult()
        val vm = viewModel()

        vm.resync()
        advanceUntilIdle()
        vm.consumeSyncMessage()

        assertNull(vm.uiState.value.syncMessage)
    }
}
