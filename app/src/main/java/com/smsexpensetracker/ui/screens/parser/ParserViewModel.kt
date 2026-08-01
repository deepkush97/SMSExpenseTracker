package com.smsexpensetracker.ui.screens.parser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.parser.ParserEngine
import com.smsexpensetracker.core.parser.SenderDetector
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import com.smsexpensetracker.domain.value.ParsedResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class ParserUiState(
    val smsInput: String = "",
    val senderInput: String = "",
    val selectedBankId: Long? = null,
    val banks: List<Bank> = emptyList(),
    val displayRules: List<SmsRule> = emptyList(),
    val result: ParsedResult? = null,
    val isParsing: Boolean = false,
    val isSaving: Boolean = false,
    val showSavedSnackbar: Boolean = false,
    val saveError: String? = null
)

@HiltViewModel
class ParserViewModel @Inject constructor(
    private val bankRepository: BankRepository,
    private val smsRuleRepository: SmsRuleRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParserUiState())
    val uiState: StateFlow<ParserUiState> = _uiState.asStateFlow()

    private var allRules: List<SmsRule> = emptyList()

    init {
        viewModelScope.launch {
            val banks = bankRepository.getAllBanks().first()
            allRules = smsRuleRepository.getAllRules().first()
            _uiState.update { it.copy(banks = banks) }
            refreshDisplayRules()
        }
    }

    fun onSmsChange(value: String) = _uiState.update {
        it.copy(smsInput = value, result = null)
    }

    fun onSenderChange(value: String) {
        _uiState.update { it.copy(senderInput = value, result = null) }
        refreshDisplayRules()
    }

    fun onBankSelect(id: Long?) {
        _uiState.update { it.copy(selectedBankId = id, result = null) }
        refreshDisplayRules()
    }

    fun detectBank(sender: String, banks: List<Bank>): Long? {
        val cleaned = SenderDetector.detect(sender).value.uppercase()
        if (cleaned.isBlank()) return null
        return banks.firstOrNull { bank ->
            val smsSender = bank.smsSender.uppercase()
            cleaned == smsSender || cleaned.contains(smsSender) || smsSender.contains(cleaned)
        }?.id
    }

    private fun resolveRules(current: ParserUiState): Pair<Long?, List<SmsRule>> {
        val bankId = current.selectedBankId ?: detectBank(current.senderInput, current.banks)
        val rules = if (bankId != null) allRules.filter { it.bankId == bankId && it.isActive } else emptyList()
        return bankId to rules
    }

    private fun refreshDisplayRules() {
        val (_, rules) = resolveRules(_uiState.value)
        _uiState.update { it.copy(displayRules = rules) }
    }

    fun parse() {
        val current = _uiState.value
        if (current.isParsing || current.smsInput.isBlank()) return

        val (bankId, rules) = resolveRules(current)
        val rulePatterns = rules.map { it.bankId to it.pattern }

        _uiState.update { it.copy(isParsing = true) }
        val result = ParserEngine.parse(current.smsInput, current.senderInput, rulePatterns)
        _uiState.update { it.copy(result = result, isParsing = false) }
    }

    fun addAsTransaction() {
        val current = _uiState.value
        val result = current.result
        val bankId = result?.bankId
        if (current.isSaving || result == null || bankId == null || result.amount <= 0) return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                transactionRepository.insert(
                    Transaction(
                        id = 0L,
                        bankId = bankId,
                        amount = result.amount,
                        transactionType = result.type,
                        description = result.description,
                        transactionDate = LocalDate.now().atStartOfDay(),
                        categoryId = null,
                        rawSms = result.rawSms,
                        smsTimestamp = 0L,
                        createdAt = LocalDateTime.now(),
                        parseMethod = ParseMethod.MANUAL
                    )
                )
                _uiState.update {
                    it.copy(result = null, isSaving = false, showSavedSnackbar = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, saveError = "Could not save transaction. Please try again.")
                }
            }
        }
    }

    fun consumeSavedSnackbar() = _uiState.update { it.copy(showSavedSnackbar = false) }

    fun consumeSaveError() = _uiState.update { it.copy(saveError = null) }
}
