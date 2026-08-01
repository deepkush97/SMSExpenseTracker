package com.smsexpensetracker.ui.screens.banks

import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.repository.BankRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BankManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BankRepository>()

    private val hdfc = Bank(id = 1, name = "HDFC Bank", smsSender = "HDFCBK")
    private val icici = Bank(id = 2, name = "ICICI Bank", smsSender = "ICICIB")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `banks flow emits repository list`() = runTest(testDispatcher) {
        every { repository.getAllBanks() } returns flowOf(listOf(hdfc, icici))
        coEvery { repository.countTransactions(1L) } returns 0
        coEvery { repository.countTransactions(2L) } returns 0
        val viewModel = BankManagementViewModel(repository)
        val job = launch { viewModel.banks.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(hdfc, icici), viewModel.banks.value)
        job.cancel()
    }

    @Test
    fun `addBank inserts with trimmed and uppercased sender`() = runTest(testDispatcher) {
        every { repository.getAllBanks() } returns flowOf(emptyList())
        coEvery { repository.insert(any()) } returns 3L
        val viewModel = BankManagementViewModel(repository)
        viewModel.addBank("  Axis Bank ", " axisb ")
        advanceUntilIdle()
        coVerify {
            repository.insert(Bank(id = 0, name = "Axis Bank", smsSender = "AXISB"))
        }
    }

    @Test
    fun `updateBank updates the bank`() = runTest(testDispatcher) {
        every { repository.getAllBanks() } returns flowOf(emptyList())
        coEvery { repository.update(any()) } returns Unit
        val viewModel = BankManagementViewModel(repository)
        val updated = hdfc.copy(name = "HDFC Bank Ltd")
        viewModel.updateBank(updated)
        advanceUntilIdle()
        coVerify { repository.update(updated) }
    }

    @Test
    fun `deleteBank deletes bank with zero transactions`() = runTest(testDispatcher) {
        every { repository.getAllBanks() } returns flowOf(emptyList())
        coEvery { repository.countTransactions(1L) } returns 0
        coEvery { repository.delete(any()) } returns Unit
        val viewModel = BankManagementViewModel(repository)
        viewModel.deleteBank(hdfc)
        advanceUntilIdle()
        coVerify { repository.delete(hdfc) }
    }

    @Test
    fun `deleteBank guards bank with transactions`() = runTest(testDispatcher) {
        every { repository.getAllBanks() } returns flowOf(emptyList())
        coEvery { repository.countTransactions(1L) } returns 3
        val viewModel = BankManagementViewModel(repository)
        viewModel.deleteBank(hdfc)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.delete(any()) }
    }
}
