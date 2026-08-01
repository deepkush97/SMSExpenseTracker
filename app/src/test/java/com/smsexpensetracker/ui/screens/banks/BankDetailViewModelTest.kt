package com.smsexpensetracker.ui.screens.banks

import androidx.lifecycle.SavedStateHandle
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
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
class BankDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val bankRepository = mockk<BankRepository>()
    private val ruleRepository = mockk<SmsRuleRepository>()

    private val hdfc = Bank(id = 1, name = "HDFC Bank", smsSender = "HDFCBK")
    private val rule = SmsRule(
        id = 1L,
        bankId = 1L,
        pattern = "Spent Rs\\.([\\d,.]+) On HDFC Bank Card",
        description = "HDFC CC Debit",
        isActive = true
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: Map<String, Any> = mapOf("bankId" to 1L)) =
        BankDetailViewModel(SavedStateHandle(savedState), bankRepository, ruleRepository)

    @Test
    fun `bank flow emits bank by id`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(emptyList())
        val viewModel = viewModel()
        val job = launch { viewModel.bank.collect {} }
        advanceUntilIdle()
        assertEquals(hdfc, viewModel.bank.value)
        job.cancel()
    }

    @Test
    fun `rules flow emits rules for bank`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(listOf(rule))
        val viewModel = viewModel()
        val job = launch { viewModel.rules.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(rule), viewModel.rules.value)
        job.cancel()
    }

    @Test
    fun `addRule inserts with bank id and isActive true`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(emptyList())
        coEvery { ruleRepository.insert(any()) } returns 9L
        val viewModel = viewModel()
        viewModel.addRule("Axis UPI", "Acct \\w+ credited")
        advanceUntilIdle()
        coVerify {
            ruleRepository.insert(
                SmsRule(id = 0L, bankId = 1L, pattern = "Acct \\w+ credited", description = "Axis UPI", isActive = true)
            )
        }
    }

    @Test
    fun `updateRule updates the rule`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(emptyList())
        coEvery { ruleRepository.update(any()) } returns Unit
        val viewModel = viewModel()
        val updated = rule.copy(description = "HDFC Debit v2")
        viewModel.updateRule(updated)
        advanceUntilIdle()
        coVerify { ruleRepository.update(updated) }
    }

    @Test
    fun `deleteRule deletes the rule`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(emptyList())
        coEvery { ruleRepository.delete(any()) } returns Unit
        val viewModel = viewModel()
        viewModel.deleteRule(rule)
        advanceUntilIdle()
        coVerify { ruleRepository.delete(rule) }
    }

    @Test
    fun `setRuleActive flips isActive`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(emptyList())
        coEvery { ruleRepository.update(any()) } returns Unit
        val viewModel = viewModel()
        viewModel.setRuleActive(rule, false)
        advanceUntilIdle()
        coVerify { ruleRepository.update(rule.copy(isActive = false)) }
    }
}
