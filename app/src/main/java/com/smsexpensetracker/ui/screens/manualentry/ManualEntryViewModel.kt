package com.smsexpensetracker.ui.screens.manualentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.parser.parsePaisa
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class FormErrors(
    val amount: String? = null,
    val payee: String? = null
)

data class ManualEntryUiState(
    val amountInput: String = "",
    val type: TransactionType = TransactionType.DEBIT,
    val transactionDate: LocalDate = LocalDate.now(),
    val bankId: Long? = null,
    val payee: String = "",
    val reference: String = "",
    val categoryId: Long? = null,
    val banks: List<Bank> = emptyList(),
    val categories: List<Category> = emptyList(),
    val errors: FormErrors = FormErrors(),
    val isSaving: Boolean = false,
    val showSavedSnackbar: Boolean = false
)

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val bankRepository: BankRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualEntryUiState())
    val uiState: StateFlow<ManualEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val banks = bankRepository.getAllBanks().first()
            val categories = categoryRepository.getAllCategories().first()
            _uiState.update {
                it.copy(
                    banks = banks,
                    categories = categories,
                    bankId = it.bankId ?: banks.firstOrNull()?.id
                )
            }
        }
    }

    fun onAmountChange(value: String) = _uiState.update {
        it.copy(
            amountInput = value.filter { c -> c.isDigit() || c == '.' || c == ',' },
            errors = it.errors.copy(amount = null)
        )
    }

    fun onTypeChange(type: TransactionType) = _uiState.update { it.copy(type = type) }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(transactionDate = date) }

    fun onBankChange(id: Long) = _uiState.update { it.copy(bankId = id) }

    fun onPayeeChange(value: String) = _uiState.update {
        it.copy(payee = value, errors = it.errors.copy(payee = null))
    }

    fun onReferenceChange(value: String) = _uiState.update { it.copy(reference = value) }

    fun onCategoryChange(id: Long?) = _uiState.update { it.copy(categoryId = id) }

    fun save() {
        val current = _uiState.value
        if (current.isSaving) return

        val amountPaisa = parsePaisa(current.amountInput)
        val errors = FormErrors(
            amount = when {
                current.amountInput.isBlank() -> "Amount is required"
                amountPaisa == null -> "Enter a valid amount"
                amountPaisa <= 0 -> "Amount must be greater than zero"
                else -> null
            },
            payee = when {
                current.payee.isBlank() -> "Payee is required"
                current.payee.length > 200 -> "Payee must be 200 characters or fewer"
                else -> null
            }
        )

        if (errors.amount != null || errors.payee != null) {
            _uiState.update { it.copy(errors = errors) }
            return
        }

        val bankId = current.bankId ?: return
        val paisa = amountPaisa ?: return
        val description = if (current.reference.isBlank()) {
            current.payee.trim()
        } else {
            "${current.payee.trim()} · ${current.reference.trim()}"
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            transactionRepository.insert(
                Transaction(
                    id = 0L,
                    bankId = bankId,
                    amount = paisa,
                    transactionType = current.type,
                    description = description,
                    transactionDate = current.transactionDate.atStartOfDay(),
                    categoryId = current.categoryId,
                    rawSms = "",
                    smsTimestamp = 0L,
                    createdAt = LocalDateTime.now(),
                    parseMethod = ParseMethod.MANUAL
                )
            )
            _uiState.update {
                it.copy(
                    amountInput = "",
                    payee = "",
                    reference = "",
                    categoryId = null,
                    errors = FormErrors(),
                    isSaving = false,
                    showSavedSnackbar = true
                )
            }
        }
    }

    fun consumeSavedSnackbar() = _uiState.update { it.copy(showSavedSnackbar = false) }
}
