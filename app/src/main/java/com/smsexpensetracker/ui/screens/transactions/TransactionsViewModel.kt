package com.smsexpensetracker.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val syncMessage: String? = null
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
        _syncMessage
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
            syncMessage = syncMessage
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
