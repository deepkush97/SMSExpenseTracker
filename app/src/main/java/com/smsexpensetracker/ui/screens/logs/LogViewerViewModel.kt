package com.smsexpensetracker.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.data.logging.FileLogger
import com.smsexpensetracker.data.logging.LogFile
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.repository.ParseLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    parseLogRepository: ParseLogRepository,
    private val fileLogger: FileLogger
) : ViewModel() {

    val parseLogs: StateFlow<List<ParseLog>> = parseLogRepository.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _fileLogs = MutableStateFlow<Map<LogFile, String>>(emptyMap())
    val fileLogs: StateFlow<Map<LogFile, String>> = _fileLogs.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _fileLogs.value = fileLogger.readAll() }
    }

    fun clearFile(file: LogFile) {
        viewModelScope.launch {
            fileLogger.clear(file)
            _fileLogs.value = fileLogger.readAll()
        }
    }

    fun logFileUri(file: LogFile): android.net.Uri = fileLogger.logFileUri(file)
}
