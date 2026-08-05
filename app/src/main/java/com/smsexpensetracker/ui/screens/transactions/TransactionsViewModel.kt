package com.smsexpensetracker.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.parser.parsePaisa
import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.data.demo.DemoDataSeeder
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import com.smsexpensetracker.domain.usecase.GetTransactionsUseCase
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import com.smsexpensetracker.ui.util.formatPaisaInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject

data class MonthlyCategoryItem(val categoryName: String, val color: Int, val amount: Long)

data class TransactionsUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val monthlyCredits: Long = 0,
    val monthlyDebits: Long = 0,
    val netAmount: Long = 0,
    val displayedTransactions: List<Transaction> = emptyList(),
    val monthlyCategoryBreakdown: List<MonthlyCategoryItem> = emptyList(),
    val searchQuery: String = "",
    val filterType: TransactionType? = null,
    val selectedBankId: Long? = null,
    val banks: List<Bank> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedTransaction: Transaction? = null,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val editAmountInput: String = "",
    val editType: TransactionType = TransactionType.DEBIT,
    val editDateTime: LocalDateTime? = null,
    val editBankId: Long? = null,
    val editDescription: String = "",
    val editCategoryId: Long? = null,
    val editErrors: EditFormErrors = EditFormErrors(),
    val isUpdating: Boolean = false,
    val showEditSavedSnackbar: Boolean = false,
    val editSaveError: String? = null
)

private data class EditFormState(
    val amountInput: String = "",
    val type: TransactionType = TransactionType.DEBIT,
    val dateTime: LocalDateTime? = null,
    val bankId: Long? = null,
    val description: String = "",
    val categoryId: Long? = null,
    val errors: EditFormErrors = EditFormErrors(),
    val isUpdating: Boolean = false,
    val showSavedSnackbar: Boolean = false,
    val saveError: String? = null
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val bankRepository: BankRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val smsSyncUseCase: SmsSyncUseCase,
    private val demoDataPreferences: DemoDataPreferences,
    private val demoDataSeeder: DemoDataSeeder
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow<TransactionType?>(null)
    val filterType: StateFlow<TransactionType?> = _filterType.asStateFlow()

    private val _selectedBankId = MutableStateFlow<Long?>(null)
    val selectedBankId: StateFlow<Long?> = _selectedBankId.asStateFlow()

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _selectedTransaction = MutableStateFlow<Transaction?>(null)
    val selectedTransaction: StateFlow<Transaction?> = _selectedTransaction.asStateFlow()

    private val _editForm = MutableStateFlow(EditFormState())

    private val _isSyncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)

    private val _demoDataLoaded = MutableStateFlow(false)
    private val _showDemoBarrier = MutableStateFlow(false)
    val showDemoBarrier: StateFlow<Boolean> = _showDemoBarrier.asStateFlow()

    init {
        viewModelScope.launch {
            demoDataPreferences.demoDataLoaded.collect { _demoDataLoaded.value = it }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<TransactionsUiState> = combine(
        getTransactionsUseCase(),
        bankRepository.getAllBanks(),
        categoryRepository.getAllCategories(),
        _searchQuery,
        _filterType,
        _selectedBankId,
        _currentMonth,
        _isSyncing,
        _syncMessage,
        _editForm
    ) { array ->
        val allTxs = array[0] as List<Transaction>
        val banks = array[1] as List<Bank>
        val categories = array[2] as List<Category>
        val query = array[3] as String
        val type = array[4] as TransactionType?
        val bankId = array[5] as Long?
        val month = array[6] as YearMonth
        val isSyncing = array[7] as Boolean
        val syncMessage = array[8] as String?
        val edit = array[9] as EditFormState

        val monthTxs = allTxs.filter { tx ->
            YearMonth.from(tx.transactionDate) == month
        }

        val credits = monthTxs.filter { it.transactionType == TransactionType.CREDIT }.sumOf { it.amount }
        val debits = monthTxs.filter { it.transactionType == TransactionType.DEBIT }.sumOf { it.amount }

        val catMap = categories.associateBy { it.id }
        val categoryBreakdown = monthTxs
            .filter { it.transactionType == TransactionType.DEBIT }
            .groupBy { it.categoryId }
            .map { (catId, txs) ->
                MonthlyCategoryItem(
                    categoryName = catId?.let { catMap[it]?.name } ?: "Uncategorized",
                    color = catId?.let { catMap[it]?.color } ?: 0xFF6B7280.toInt(),
                    amount = txs.sumOf { it.amount }
                )
            }
            .sortedByDescending { it.amount }

        val displayed = monthTxs.filter { tx ->
            (type == null || tx.transactionType == type) &&
                (bankId == null || tx.bankId == bankId) &&
                (query.isBlank() || tx.description.contains(query, ignoreCase = true))
        }.sortedByDescending { it.transactionDate }

        TransactionsUiState(
            currentMonth = month,
            monthlyCredits = credits,
            monthlyDebits = debits,
            netAmount = credits - debits,
            displayedTransactions = displayed,
            monthlyCategoryBreakdown = categoryBreakdown,
            searchQuery = query,
            filterType = type,
            selectedBankId = bankId,
            banks = banks,
            categories = categories,
            selectedTransaction = _selectedTransaction.value,
            isLoading = false,
            isSyncing = isSyncing,
            syncMessage = syncMessage,
            editAmountInput = edit.amountInput,
            editType = edit.type,
            editDateTime = edit.dateTime,
            editBankId = edit.bankId,
            editDescription = edit.description,
            editCategoryId = edit.categoryId,
            editErrors = edit.errors,
            isUpdating = edit.isUpdating,
            showEditSavedSnackbar = edit.showSavedSnackbar,
            editSaveError = edit.saveError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsUiState())

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun onFilterTypeChange(type: TransactionType?) { _filterType.value = type }
    fun onBankChange(bankId: Long?) { _selectedBankId.value = bankId }
    fun onMonthChange(month: YearMonth) {
        if (!month.isAfter(YearMonth.now())) _currentMonth.value = month
    }
    fun onTransactionClick(tx: Transaction) {
        _selectedTransaction.value = tx
        _editForm.value = EditFormState(
            amountInput = formatPaisaInput(tx.amount),
            type = tx.transactionType,
            dateTime = tx.transactionDate,
            bankId = tx.bankId,
            description = tx.description,
            categoryId = tx.categoryId
        )
    }

    fun onDismissSheet() {
        _selectedTransaction.value = null
        _editForm.value = EditFormState()
    }

    fun onEditAmountChange(value: String) = _editForm.update {
        it.copy(
            amountInput = value.filter { c -> c.isDigit() || c == '.' || c == ',' },
            errors = it.errors.copy(amount = null)
        )
    }

    fun onEditTypeChange(type: TransactionType) = _editForm.update { it.copy(type = type) }

    fun onEditDateChange(date: LocalDate) = _editForm.update {
        it.copy(dateTime = it.dateTime?.with(date) ?: date.atStartOfDay())
    }

    fun onEditBankChange(id: Long) = _editForm.update { it.copy(bankId = id) }

    fun onEditDescriptionChange(value: String) = _editForm.update {
        it.copy(description = value, errors = it.errors.copy(description = null))
    }

    fun onEditCategoryChange(id: Long?) = _editForm.update { it.copy(categoryId = id) }

    fun updateTransaction() {
        val tx = _selectedTransaction.value ?: return
        val form = _editForm.value
        if (form.isUpdating) return
        if (_demoDataLoaded.value) {
            _showDemoBarrier.value = true
            return
        }

        val errors = validateTransactionEdit(form.amountInput, form.description)
        if (errors.amount != null || errors.description != null) {
            _editForm.update { it.copy(errors = errors) }
            return
        }

        val bankId = form.bankId ?: return
        val amount = parsePaisa(form.amountInput) ?: return
        val dateTime = form.dateTime ?: tx.transactionDate

        _editForm.update { it.copy(isUpdating = true) }
        viewModelScope.launch {
            try {
                transactionRepository.updateEditedTransaction(
                    tx.copy(
                        bankId = bankId,
                        amount = amount,
                        transactionType = form.type,
                        description = form.description.trim(),
                        transactionDate = dateTime,
                        categoryId = form.categoryId
                    )
                )
                _selectedTransaction.value = null
                _editForm.update {
                    it.copy(
                        amountInput = "",
                        type = TransactionType.DEBIT,
                        dateTime = null,
                        bankId = null,
                        description = "",
                        categoryId = null,
                        errors = EditFormErrors(),
                        isUpdating = false,
                        showSavedSnackbar = true,
                        saveError = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _editForm.update {
                    it.copy(isUpdating = false, saveError = "Could not update transaction. Please try again.")
                }
            }
        }
    }

    fun consumeEditSavedSnackbar() = _editForm.update { it.copy(showSavedSnackbar = false) }

    fun consumeEditSaveError() = _editForm.update { it.copy(saveError = null) }

    fun sync() {
        if (_isSyncing.value) return
        if (_demoDataLoaded.value) {
            _showDemoBarrier.value = true
            return
        }
        _isSyncing.value = true
        viewModelScope.launch {
            val result = smsSyncUseCase.sync()
            _syncMessage.value = if (result.error != null) {
                "Sync failed. Try again."
            } else {
                "Scanned ${result.scanned}, added ${result.inserted}, unparsed ${result.unparsed}"
            }
            _isSyncing.value = false
        }
    }

    fun dismissDemoBarrier() {
        _showDemoBarrier.value = false
    }

    fun confirmDeleteDemoData() {
        viewModelScope.launch {
            demoDataSeeder.deleteDemoData()
            _showDemoBarrier.value = false
        }
    }

    fun consumeSyncMessage() {
        _syncMessage.value = null
    }
}
