package com.smsexpensetracker.ui.screens.banks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BankDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bankRepository: BankRepository,
    private val smsRuleRepository: SmsRuleRepository
) : ViewModel() {

    private val bankId: Long = checkNotNull(savedStateHandle["bankId"])

    val bank: StateFlow<Bank?> = flow {
        emit(bankRepository.getBankById(bankId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val rules: StateFlow<List<SmsRule>> = smsRuleRepository.getRulesForBank(bankId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRule(description: String, pattern: String, isActive: Boolean = true) {
        viewModelScope.launch {
            smsRuleRepository.insert(
                SmsRule(id = 0L, bankId = bankId, pattern = pattern.trim(), description = description.trim(), isActive = isActive)
            )
        }
    }

    fun updateRule(rule: SmsRule) {
        viewModelScope.launch {
            smsRuleRepository.update(rule)
        }
    }

    fun deleteRule(rule: SmsRule) {
        viewModelScope.launch {
            smsRuleRepository.delete(rule)
        }
    }

    fun setRuleActive(rule: SmsRule, active: Boolean) {
        viewModelScope.launch {
            smsRuleRepository.update(rule.copy(isActive = active))
        }
    }
}
