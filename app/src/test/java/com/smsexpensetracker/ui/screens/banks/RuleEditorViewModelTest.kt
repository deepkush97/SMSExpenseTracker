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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val bankRepository = mockk<BankRepository>()
    private val ruleRepository = mockk<SmsRuleRepository>()

    private val hdfc = Bank(id = 1, name = "HDFC Bank", smsSender = "HDFCBK")
    private val existingRule = SmsRule(
        id = 7L,
        bankId = 1L,
        pattern = "Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
        description = "HDFC CC Debit",
        isActive = true
    )
    private val smsBody = "Spent Rs.1250.50 On HDFC Bank Card 1234 At Coffee Shop On 01-Aug"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        savedState: Map<String, Any> = mapOf("bankId" to 1L, "ruleId" to -1L)
    ) = RuleEditorViewModel(SavedStateHandle(savedState), bankRepository, ruleRepository)

    @Test
    fun `bank flow emits bank by id`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        val job = launch { vm.bank.collect {} }
        advanceUntilIdle()
        assertEquals(hdfc, vm.bank.value)
        job.cancel()
    }

    @Test
    fun `add mode starts with empty fields`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals("", state.sampleSms)
        assertEquals("", state.draftPattern)
        assertEquals("", state.description)
        assertNull(state.testResult)
        assertFalse(state.hasTested)
    }

    @Test
    fun `edit mode pre-fills pattern and description from existing rule`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        coEvery { ruleRepository.getRuleById(7L) } returns existingRule
        val vm = viewModel(mapOf("bankId" to 1L, "ruleId" to 7L))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(existingRule.pattern, state.draftPattern)
        assertEquals(existingRule.description, state.description)
    }

    @Test
    fun `test with matching pattern sets testResult`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange(existingRule.pattern)
        vm.onTest()
        val state = vm.uiState.value
        assertTrue(state.hasTested)
        assertNotNull(state.testResult)
        assertEquals(125050L, state.testResult?.amount)
        assertEquals("Coffee Shop", state.testResult?.description)
    }

    @Test
    fun `test with non-matching pattern sets hasTested true and testResult null`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange("ICICI Bank Acct 1234 debited for Rs 500.00 on 01-Aug; Swiggy credited. UPI")
        vm.onPatternChange(existingRule.pattern)
        vm.onTest()
        val state = vm.uiState.value
        assertTrue(state.hasTested)
        assertNull(state.testResult)
    }

    @Test
    fun `test with pattern whose group 1 is not an amount returns no match`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange("Spent (abc) On HDFC Bank Card \\d{4} At (.+?) On .+")
        vm.onTest()
        val state = vm.uiState.value
        assertTrue(state.hasTested)
        assertNull(state.testResult)
    }

    @Test
    fun `changing sample clears previous test result`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange(existingRule.pattern)
        vm.onTest()
        assertTrue(vm.uiState.value.hasTested)
        vm.onSampleSmsChange("changed")
        val state = vm.uiState.value
        assertFalse(state.hasTested)
        assertNull(state.testResult)
    }

    @Test
    fun `onSave in add mode inserts rule with trimmed values`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        coEvery { ruleRepository.insert(any()) } returns 9L
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange("  Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+  ")
        vm.onDescriptionChange("  HDFC CC Debit  ")
        vm.onSave()
        advanceUntilIdle()
        coVerify {
            ruleRepository.insert(
                SmsRule(
                    id = 0L,
                    bankId = 1L,
                    pattern = "Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
                    description = "HDFC CC Debit",
                    isActive = true
                )
            )
        }
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `onSave in edit mode updates existing rule preserving id and isActive`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        coEvery { ruleRepository.getRuleById(7L) } returns existingRule
        coEvery { ruleRepository.update(any()) } returns Unit
        val vm = viewModel(mapOf("bankId" to 1L, "ruleId" to 7L))
        advanceUntilIdle()
        vm.onDescriptionChange("HDFC CC Debit v2")
        vm.onSave()
        advanceUntilIdle()
        coVerify {
            ruleRepository.update(
                existingRule.copy(description = "HDFC CC Debit v2")
            )
        }
    }

    @Test
    fun `onSave before pre-fill completes is ignored then saves after load`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        coEvery { ruleRepository.getRuleById(7L) } returns existingRule
        coEvery { ruleRepository.update(any()) } returns Unit
        val vm = viewModel(mapOf("bankId" to 1L, "ruleId" to 7L))
        vm.onSave()
        assertFalse(vm.uiState.value.saved)
        advanceUntilIdle()
        coVerify(exactly = 0) { ruleRepository.update(any()) }
        vm.onDescriptionChange("HDFC CC Debit v2")
        vm.onSave()
        advanceUntilIdle()
        coVerify {
            ruleRepository.update(
                existingRule.copy(description = "HDFC CC Debit v2")
            )
        }
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `save failure sets saveError`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        coEvery { ruleRepository.insert(any()) } throws RuntimeException("db down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange(existingRule.pattern)
        vm.onDescriptionChange("HDFC CC Debit")
        vm.onSave()
        advanceUntilIdle()
        assertEquals("Could not save rule. Please try again.", vm.uiState.value.saveError)
        assertFalse(vm.uiState.value.saved)
    }

    @Test
    fun `consumeSaveError clears saveError`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        coEvery { ruleRepository.insert(any()) } throws RuntimeException("db down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange(existingRule.pattern)
        vm.onDescriptionChange("HDFC CC Debit")
        vm.onSave()
        advanceUntilIdle()
        vm.consumeSaveError()
        assertNull(vm.uiState.value.saveError)
    }
}
