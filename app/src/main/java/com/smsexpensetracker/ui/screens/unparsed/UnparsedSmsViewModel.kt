package com.smsexpensetracker.ui.screens.unparsed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.parser.detectBankForSender
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.ParseLogRepository
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

enum class UnparsedFilter { FAILED, ALL }

data class FailedSms(
    val smsBody: String,
    val smsSender: String,
    val errorMessage: String?,
    val lastParsedAt: LocalDateTime,
    val failCount: Int,
    val bankId: Long?
)

data class UnparsedSmsUiState(
    val filter: UnparsedFilter = UnparsedFilter.FAILED,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null
)

@HiltViewModel
class UnparsedSmsViewModel @Inject constructor(
    private val parseLogRepository: ParseLogRepository,
    private val bankRepository: BankRepository,
    private val smsSyncUseCase: SmsSyncUseCase
) : ViewModel() {

    val parseLogs: StateFlow<List<ParseLog>> = parseLogRepository.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val banks: StateFlow<List<Bank>> = bankRepository.getAllBanks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val failedLogs: StateFlow<List<FailedSms>> =
        combine(parseLogs, banks) { logs, banks ->
            logs.filter { it.status == ParseStatus.FAILED }
                .groupBy { it.smsBody }
                .map { (_, group) ->
                    val newest = group.maxBy { it.parsedAt }
                    FailedSms(
                        smsBody = newest.smsBody,
                        smsSender = newest.smsSender,
                        errorMessage = newest.errorMessage,
                        lastParsedAt = newest.parsedAt,
                        failCount = group.size,
                        bankId = detectBankForSender(newest.smsSender, banks)
                    )
                }
                .sortedByDescending { it.lastParsedAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(UnparsedSmsUiState())
    val uiState: StateFlow<UnparsedSmsUiState> = _uiState.asStateFlow()

    fun setFilter(filter: UnparsedFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun resync() {
        if (_uiState.value.isSyncing) return
        _uiState.update { it.copy(isSyncing = true, syncMessage = null) }
        viewModelScope.launch {
            try {
                parseLogRepository.deleteFailed()
                val result = smsSyncUseCase.sync()
                val message = if (result.error != null) {
                    "Sync failed. Try again."
                } else {
                    "Scanned ${result.scanned}, added ${result.inserted}, unparsed ${result.unparsed}"
                }
                _uiState.update { it.copy(isSyncing = false, syncMessage = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSyncing = false, syncMessage = "Sync failed. Try again.") }
            }
        }
    }

    fun consumeSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }
}
