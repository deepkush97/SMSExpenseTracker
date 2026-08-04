package com.smsexpensetracker.ui.screens.categorize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.Transaction
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
import javax.inject.Inject

data class CategorizeUiState(
    val queue: List<Transaction> = emptyList(),
    val index: Int = 0,
    val categories: List<Category> = emptyList(),
    val banks: List<Bank> = emptyList(),
    val assignedCount: Int = 0
) {
    val current: Transaction? get() = queue.getOrNull(index)
    val isDone: Boolean get() = queue.isNotEmpty() && index >= queue.size
}

@HiltViewModel
class CategorizeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val bankRepository: BankRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategorizeUiState())
    val uiState: StateFlow<CategorizeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val transactions = transactionRepository.getAllTransactions().first()
            val categories = categoryRepository.getAllCategories().first()
            val banks = bankRepository.getAllBanks().first()
            val queue = transactions.sortedWith(
                compareBy<Transaction> { it.categoryId != null }
                    .thenByDescending { it.transactionDate }
            )
            _uiState.update { it.copy(queue = queue, categories = categories, banks = banks) }
        }
    }

    fun assignCategory(categoryId: Long?) {
        val state = _uiState.value
        val current = state.current ?: return
        viewModelScope.launch {
            transactionRepository.updateTransactionCategory(current.id, categoryId)
            _uiState.update {
                it.copy(index = it.index + 1, assignedCount = it.assignedCount + 1)
            }
        }
    }

    fun skip() {
        _uiState.update { it.copy(index = it.index + 1) }
    }

    fun reset() {
        _uiState.update { it.copy(index = 0, assignedCount = 0) }
    }
}
