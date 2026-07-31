package com.smsexpensetracker.ui.screens.manualentry

import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ManualEntryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val bankRepository = mockk<BankRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val transactionRepository = mockk<TransactionRepository>()

    private val hdfc = Bank(id = 1, name = "HDFC Bank", smsSender = "HDFCBK")
    private val icici = Bank(id = 2, name = "ICICI Bank", smsSender = "ICICIB")
    private val food = Category(id = 3, name = "Food", icon = "", color = 0xFF0000, isDefault = false)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { bankRepository.getAllBanks() } returns flowOf(listOf(hdfc, icici))
        every { categoryRepository.getAllCategories() } returns flowOf(listOf(food))
        coEvery { transactionRepository.insert(any()) } returns 1L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ManualEntryViewModel =
        ManualEntryViewModel(bankRepository, categoryRepository, transactionRepository)

    @Test
    fun `init defaults bank to first bank`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(1L, vm.uiState.value.bankId)
        assertEquals(listOf(hdfc, icici), vm.uiState.value.banks)
    }

    @Test
    fun `blank amount shows error and does not insert`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onPayeeChange("Zomato")
        vm.save()
        advanceUntilIdle()
        assertEquals("Amount is required", vm.uiState.value.errors.amount)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `blank amount and payee show both errors`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertEquals("Amount is required", vm.uiState.value.errors.amount)
        assertEquals("Payee is required", vm.uiState.value.errors.payee)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `invalid amount shows error and does not insert`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("1.2.3")
        vm.onPayeeChange("Zomato")
        vm.save()
        advanceUntilIdle()
        assertEquals("Enter a valid amount", vm.uiState.value.errors.amount)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `zero amount shows error and does not insert`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("0")
        vm.onPayeeChange("Zomato")
        vm.save()
        advanceUntilIdle()
        assertEquals("Amount must be greater than zero", vm.uiState.value.errors.amount)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `blank payee shows error and does not insert`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100.50")
        vm.save()
        advanceUntilIdle()
        assertEquals("Payee is required", vm.uiState.value.errors.payee)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `overlong payee shows error and does not insert`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100.50")
        vm.onPayeeChange("x".repeat(201))
        vm.save()
        advanceUntilIdle()
        assertEquals("Payee must be 200 characters or fewer", vm.uiState.value.errors.payee)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `valid save inserts transaction in paisa with manual parseMethod`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("1,250.50")
        vm.onPayeeChange("Zomato")
        vm.onReferenceChange("ORD-123")
        vm.onTypeChange(TransactionType.CREDIT)
        vm.onCategoryChange(3)
        vm.save()
        advanceUntilIdle()

        coVerify {
            transactionRepository.insert(
                match<Transaction> {
                    it.amount == 125050L &&
                        it.transactionType == TransactionType.CREDIT &&
                        it.description == "Zomato · ORD-123" &&
                        it.categoryId == 3L &&
                        it.parseMethod == ParseMethod.MANUAL &&
                        it.rawSms == "" &&
                        it.smsTimestamp == 0L
                }
            )
        }
    }

    @Test
    fun `save without reference uses payee as description`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("45")
        vm.onPayeeChange("Zomato")
        vm.save()
        advanceUntilIdle()
        coVerify {
            transactionRepository.insert(match<Transaction> { it.description == "Zomato" && it.amount == 4500L })
        }
    }

    @Test
    fun `save uses chosen date at start of day`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100")
        vm.onPayeeChange("Zomato")
        vm.onDateChange(LocalDate.of(2026, 7, 15))
        vm.save()
        advanceUntilIdle()
        coVerify {
            transactionRepository.insert(match<Transaction> { it.transactionDate.toLocalDate() == LocalDate.of(2026, 7, 15) })
        }
    }

    @Test
    fun `after save form clears and snackbar flag set`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100.50")
        vm.onPayeeChange("Zomato")
        vm.onReferenceChange("R1")
        vm.onCategoryChange(3)
        vm.save()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("", state.amountInput)
        assertEquals("", state.payee)
        assertEquals("", state.reference)
        assertNull(state.categoryId)
        assertTrue(state.showSavedSnackbar)
        assertEquals(TransactionType.DEBIT, state.type)
        assertEquals(1L, state.bankId)
    }

    @Test
    fun `consumeSavedSnackbar resets flag`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100")
        vm.onPayeeChange("Zomato")
        vm.save()
        advanceUntilIdle()
        vm.consumeSavedSnackbar()
        assertFalse(vm.uiState.value.showSavedSnackbar)
    }

    @Test
    fun `failed insert resets saving and sets save error`() = runTest(testDispatcher) {
        coEvery { transactionRepository.insert(any()) } throws RuntimeException("boom")
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100.50")
        vm.onPayeeChange("Zomato")
        vm.save()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSaving)
        assertNotNull(vm.uiState.value.saveError)
    }
}
