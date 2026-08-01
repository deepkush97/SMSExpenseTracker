package com.smsexpensetracker.ui.screens.banks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.repository.BankRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BankManagementViewModel @Inject constructor(
    private val repository: BankRepository
) : ViewModel() {

    val banks: StateFlow<List<Bank>> = repository.getAllBanks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _transactionCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val transactionCounts: StateFlow<Map<Long, Int>> = _transactionCounts.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllBanks().map { list ->
                list.associate { it.id to repository.countTransactions(it.id) }
            }.collect { _transactionCounts.value = it }
        }
    }

    fun addBank(name: String, smsSender: String) {
        viewModelScope.launch {
            repository.insert(Bank(id = 0, name = name.trim(), smsSender = smsSender.trim().uppercase()))
        }
    }

    fun updateBank(bank: Bank) {
        viewModelScope.launch {
            repository.update(bank)
        }
    }

    fun deleteBank(bank: Bank) {
        viewModelScope.launch {
            val count = repository.countTransactions(bank.id)
            if (count == 0) {
                repository.delete(bank)
            }
        }
    }
}
