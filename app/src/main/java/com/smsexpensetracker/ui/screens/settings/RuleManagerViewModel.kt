package com.smsexpensetracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.UserCategoryRule
import com.smsexpensetracker.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserCategoryRuleWithName(
    val rule: UserCategoryRule,
    val categoryName: String
)

data class RuleManagerUiState(
    val rules: List<UserCategoryRuleWithName>
)

@HiltViewModel
class RuleManagerViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val uiState: StateFlow<RuleManagerUiState> =
        combine(categoryRepository.getRules(), categoryRepository.getAllCategories()) { rules, categories ->
            val nameById = categories.associate { it.id to it.name }
            RuleManagerUiState(
                rules = rules.map { rule ->
                    UserCategoryRuleWithName(
                        rule = rule,
                        categoryName = nameById[rule.categoryId] ?: "Unknown"
                    )
                }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RuleManagerUiState(rules = emptyList())
        )

    fun delete(rule: UserCategoryRule) {
        viewModelScope.launch {
            categoryRepository.deleteRule(rule)
        }
    }
}