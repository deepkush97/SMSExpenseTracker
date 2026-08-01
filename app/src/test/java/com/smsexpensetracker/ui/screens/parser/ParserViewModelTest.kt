package com.smsexpensetracker.ui.screens.parser

import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var bankRepository: BankRepository
    private lateinit var smsRuleRepository: SmsRuleRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var viewModel: ParserViewModel

    private val hdfcBank = Bank(id = 1L, name = "HDFC Bank", smsSender = "HDFCBK")
    private val iciciBank = Bank(id = 2L, name = "ICICI Bank", smsSender = "ICICIB")

    private val hdfcDebitRule = SmsRule(
        id = 1L,
        bankId = 1L,
        pattern = "Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
        description = "HDFC CC Debit"
    )

    private val hdfcCreditRule = SmsRule(
        id = 2L,
        bankId = 1L,
        pattern = "Rs\\.([\\d,.]+) credited to HDFC Bank A/c \\w+ on [\\d-]+ from VPA (.+?) \\(UPI",
        description = "HDFC UPI Credit"
    )

    private val iciciDebitRule = SmsRule(
        id = 3L,
        bankId = 2L,
        pattern = "ICICI Bank Acct \\w+ debited for Rs ([\\d,.]+) on [\\d-]+; (.+?) credited\\. UPI",
        description = "ICICI UPI Debit"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        bankRepository = mockk()
        smsRuleRepository = mockk()
        transactionRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        banks: List<Bank> = listOf(hdfcBank, iciciBank),
        rules: List<SmsRule> = listOf(hdfcDebitRule, hdfcCreditRule, iciciDebitRule)
    ): ParserViewModel {
        every { bankRepository.getAllBanks() } returns MutableStateFlow(banks)
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(rules)
        coEvery { transactionRepository.insert(any()) } returns 1L
        return ParserViewModel(bankRepository, smsRuleRepository, transactionRepository)
    }

    @Test
    fun `parse extracts fields from valid HDFC sms`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onSmsChange(
            "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161"
        )
        viewModel.onSenderChange("AD-HDFCBK-S")
        advanceUntilIdle()
        viewModel.parse()
        advanceUntilIdle()

        val result = viewModel.uiState.value.result
        assertNotNull(result)
        assertEquals(483176L, result?.amount)
        assertEquals(TransactionType.DEBIT, result?.type)
        assertEquals("Acme Inc.", result?.description)
        assertEquals(1L, result?.bankId)
        assertEquals(1.0f, result?.confidence)
        assertNull(result?.errorMessage)
    }

    @Test
    fun `parse with unknown sender returns error`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onSmsChange("This is not a bank SMS")
        viewModel.onSenderChange("UNKNOWN-SENDER")
        advanceUntilIdle()
        viewModel.parse()
        advanceUntilIdle()

        val result = viewModel.uiState.value.result
        assertNotNull(result)
        assertNotNull(result?.errorMessage)
        assertEquals(0f, result?.confidence)
    }

    @Test
    fun `detectBank resolves TRAI sender to bank id`() = runTest {
        viewModel = createViewModel()
        assertEquals(1L, viewModel.detectBank("AD-HDFCBK-S", listOf(hdfcBank, iciciBank)))
        assertEquals(2L, viewModel.detectBank("AD-ICICIB-S", listOf(hdfcBank, iciciBank)))
        assertNull(viewModel.detectBank("UNKNOWN", listOf(hdfcBank, iciciBank)))
    }

    @Test
    fun `selected bank filters display rules`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onBankSelect(2L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.displayRules.all { it.bankId == 2L })
        assertEquals(1, state.displayRules.size)
    }

    @Test
    fun `auto-detect populates display rules from sender`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onSenderChange("AD-HDFCBK-S")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.displayRules.all { it.bankId == 1L })
        assertEquals(2, state.displayRules.size)
    }

    @Test
    fun `blank sms does not produce result`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onSenderChange("AD-HDFCBK-S")
        advanceUntilIdle()
        viewModel.parse()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `addAsTransaction inserts parsed transaction`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onSmsChange(
            "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161"
        )
        viewModel.onSenderChange("AD-HDFCBK-S")
        advanceUntilIdle()
        viewModel.parse()
        advanceUntilIdle()
        viewModel.addAsTransaction()
        advanceUntilIdle()

        coVerify {
            transactionRepository.insert(
                match {
                    it.amount == 483176L &&
                        it.bankId == 1L &&
                        it.transactionType == TransactionType.DEBIT &&
                        it.description == "Acme Inc." &&
                        it.parseMethod == ParseMethod.MANUAL &&
                        it.rawSms.isNotBlank()
                }
            )
        }
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `addAsTransaction no-ops when bank is unknown`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onSmsChange("This is not a bank SMS")
        viewModel.onSenderChange("UNKNOWN-SENDER")
        advanceUntilIdle()
        viewModel.parse()
        advanceUntilIdle()
        viewModel.addAsTransaction()
        advanceUntilIdle()

        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }
}
