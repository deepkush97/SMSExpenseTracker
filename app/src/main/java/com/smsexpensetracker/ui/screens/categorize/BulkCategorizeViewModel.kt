package com.smsexpensetracker.ui.screens.categorize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.categorize.AutoCategoryEngine
import com.smsexpensetracker.core.categorize.RuleSuggestion
import com.smsexpensetracker.core.categorize.RuleSuggestionEngine
import com.smsexpensetracker.domain.model.UserCategoryRule
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

data class SuggestionUi(
    val keyword: String,
    val transactionCount: Int,
    val chosenCategoryId: Long?,
    val enabled: Boolean
)

data class BulkCategorizeUiState(
    val isLoading: Boolean = true,
    val suggestions: List<SuggestionUi> = emptyList(),
    val uncategorizedCount: Int = 0,
    val isApplying: Boolean = false,
    val categorizedCount: Int = 0,
    val remainingCount: Int = 0
) {
    val previewCount: Int get() = suggestions.filter { it.enabled && it.chosenCategoryId != null }
        .sumOf { it.transactionCount }
}

@HiltViewModel
class BulkCategorizeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BulkCategorizeUiState())
    val uiState: StateFlow<BulkCategorizeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val transactions = transactionRepository.getAllTransactions().first()
        val classified = transactions.filter { it.categoryId != null }
        val uncategorized = transactions.filter { it.categoryId == null }
        val raw: List<RuleSuggestion> = RuleSuggestionEngine.suggest(uncategorized, classified)
        _uiState.update {
            it.copy(
                isLoading = false,
                uncategorizedCount = uncategorized.size,
                suggestions = raw.map { s ->
                    SuggestionUi(
                        keyword = s.keyword,
                        transactionCount = s.transactionCount,
                        chosenCategoryId = s.suggestedCategoryId,
                        enabled = s.suggestedCategoryId != null
                    )
                }
            )
        }
    }

    fun loadSuggestions(suggestionIndex: Int, categoryId: Long?) {
        _uiState.update { state ->
            val updated = state.suggestions.mapIndexed { i, s ->
                if (i == suggestionIndex) s.copy(chosenCategoryId = categoryId) else s
            }
            state.copy(suggestions = updated)
        }
    }

    fun setEnabled(suggestionIndex: Int, enabled: Boolean) {
        _uiState.update { state ->
            val updated = state.suggestions.mapIndexed { i, s ->
                if (i == suggestionIndex) s.copy(enabled = enabled) else s
            }
            state.copy(suggestions = updated)
        }
    }

    fun apply() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isApplying = true) }
            val confirmed = state.suggestions.filter { it.enabled && it.chosenCategoryId != null }
            val existing = categoryRepository.getRules().first()
            confirmed.forEach { s ->
                val exists = existing.any {
                    it.pattern.equals(s.keyword, ignoreCase = true) && it.categoryId == s.chosenCategoryId
                }
                if (!exists) {
                    categoryRepository.insertRule(UserCategoryRule(0L, s.keyword, s.chosenCategoryId!!))
                }
            }
            val rules = categoryRepository.getRules().first()
            val transactions = transactionRepository.getAllTransactions().first()
            val uncategorized = transactions.filter { it.categoryId == null }
            var categorized = 0
            uncategorized.forEach { t ->
                val match = AutoCategoryEngine.matchCategory(t.description, rules)
                if (match != null) {
                    transactionRepository.updateTransactionCategory(t.id, match)
                    categorized += 1
                }
            }
            _uiState.update {
                it.copy(
                    isApplying = false,
                    categorizedCount = categorized,
                    remainingCount = it.uncategorizedCount - categorized
                )
            }
        }
    }
}