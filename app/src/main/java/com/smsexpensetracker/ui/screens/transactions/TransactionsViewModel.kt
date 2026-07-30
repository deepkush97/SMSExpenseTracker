package com.smsexpensetracker.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import com.smsexpensetracker.domain.usecase.GetTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class TransactionsUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val monthlyCredits: Long = 0,
    val monthlyDebits: Long = 0,
    val netAmount: Long = 0,
    val displayedTransactions: List<Transaction> = emptyList(),
    val searchQuery: String = "",
    val filterType: TransactionType? = null,
    val selectedBankId: Long? = null,
    val banks: List<Bank> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedTransaction: Transaction? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val bankRepository: BankRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
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

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<TransactionsUiState> = combine(
        getTransactionsUseCase(),
        bankRepository.getAllBanks(),
        categoryRepository.getAllCategories(),
        _searchQuery,
        _filterType,
        _selectedBankId,
        _currentMonth
    ) { array ->
        val allTxs = array[0] as List<Transaction>
        val banks = array[1] as List<Bank>
        val categories = array[2] as List<Category>
        val query = array[3] as String
        val type = array[4] as TransactionType?
        val bankId = array[5] as Long?
        val month = array[6] as YearMonth

        val monthTxs = allTxs.filter { tx ->
            YearMonth.from(tx.transactionDate) == month
        }

        val credits = monthTxs.filter { it.transactionType == TransactionType.CREDIT }.sumOf { it.amount }
        val debits = monthTxs.filter { it.transactionType == TransactionType.DEBIT }.sumOf { it.amount }

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
            searchQuery = query,
            filterType = type,
            selectedBankId = bankId,
            banks = banks,
            categories = categories,
            selectedTransaction = _selectedTransaction.value,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsUiState())

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun onFilterTypeChange(type: TransactionType?) { _filterType.value = type }
    fun onBankChange(bankId: Long?) { _selectedBankId.value = bankId }
    fun onMonthChange(month: YearMonth) {
        if (!month.isAfter(YearMonth.now())) _currentMonth.value = month
    }
    fun onTransactionClick(tx: Transaction) { _selectedTransaction.value = tx }
    fun onDismissSheet() { _selectedTransaction.value = null }

    fun onCategoryChange(transactionId: Long, categoryId: Long?) {
        viewModelScope.launch {
            transactionRepository.updateTransactionCategory(transactionId, categoryId)
        }
    }
}
