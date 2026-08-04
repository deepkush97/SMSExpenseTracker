package com.smsexpensetracker.ui.screens.banks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.parser.RegexMatch
import com.smsexpensetracker.core.parser.RegexParser
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleEditorUiState(
    val sampleSms: String = "",
    val draftPattern: String = "",
    val description: String = "",
    val testResult: RegexMatch? = null,
    val hasTested: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null,
    val loaded: Boolean = false,
    val isSaving: Boolean = false
)

@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bankRepository: BankRepository,
    private val smsRuleRepository: SmsRuleRepository
) : ViewModel() {

    private val bankId: Long = checkNotNull(savedStateHandle["bankId"])
    private val ruleId: Long? = savedStateHandle.get<Long>("ruleId").takeIf { it != -1L }
    private var existingRule: SmsRule? = null

    private val _uiState = MutableStateFlow(
        RuleEditorUiState(sampleSms = savedStateHandle.get<String>("sampleSms").orEmpty())
    )
    val uiState: StateFlow<RuleEditorUiState> = _uiState.asStateFlow()

    val bank: StateFlow<Bank?> = kotlinx.coroutines.flow.flow {
        emit(bankRepository.getBankById(bankId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            val existing = ruleId?.let { smsRuleRepository.getRuleById(it) }
            existingRule = existing
            _uiState.update {
                it.copy(
                    draftPattern = existing?.pattern ?: it.draftPattern,
                    description = existing?.description ?: it.description,
                    loaded = true
                )
            }
        }
    }

    fun onSampleSmsChange(value: String) = _uiState.update {
        it.copy(sampleSms = value, testResult = null, hasTested = false)
    }

    fun onPatternChange(value: String) = _uiState.update {
        it.copy(draftPattern = value, testResult = null, hasTested = false)
    }

    fun onDescriptionChange(value: String) = _uiState.update {
        it.copy(description = value)
    }

    fun onTest() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                testResult = RegexParser.parse(state.sampleSms, state.draftPattern, bankId),
                hasTested = true
            )
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.saved || state.isSaving || !state.loaded) return
        _uiState.update { it.copy(isSaving = true) }
        val existing = existingRule
        viewModelScope.launch {
            try {
                if (existing == null) {
                    smsRuleRepository.insert(
                        SmsRule(
                            id = 0L,
                            bankId = bankId,
                            pattern = state.draftPattern.trim(),
                            description = state.description.trim(),
                            isActive = true
                        )
                    )
                } else {
                    smsRuleRepository.update(
                        existing.copy(
                            pattern = state.draftPattern.trim(),
                            description = state.description.trim()
                        )
                    )
                }
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveError = "Could not save rule. Please try again.") }
            }
        }
    }

    fun consumeSaveError() = _uiState.update { it.copy(saveError = null) }
}
