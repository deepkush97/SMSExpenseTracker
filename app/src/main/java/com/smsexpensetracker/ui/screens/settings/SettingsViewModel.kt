package com.smsexpensetracker.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.settings.ThemePreferences
import com.smsexpensetracker.data.csv.ExportResult
import com.smsexpensetracker.data.demo.DemoDataSeeder
import com.smsexpensetracker.domain.usecase.ExportCsvUseCase
import com.smsexpensetracker.domain.usecase.ImportCsvUseCase
import com.smsexpensetracker.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val csvMessage: String? = null,
    val isCsvBusy: Boolean = false,
    val pendingExport: ExportResult? = null,
    val demoMessage: String? = null,
    val isDemoBusy: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val exportCsvUseCase: ExportCsvUseCase,
    private val importCsvUseCase: ImportCsvUseCase,
    private val demoDataSeeder: DemoDataSeeder
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            themePreferences.themeMode.collect { theme ->
                _uiState.update { it.copy(themeMode = theme) }
            }
        }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }

    fun exportCsv() {
        if (_uiState.value.isCsvBusy) return
        _uiState.update { it.copy(isCsvBusy = true) }
        viewModelScope.launch {
            val result = exportCsvUseCase()
            _uiState.update {
                result.fold(
                    onSuccess = { export ->
                        it.copy(
                            isCsvBusy = false,
                            pendingExport = export,
                            csvMessage = "Exported ${export.count} transactions"
                        )
                    },
                    onFailure = { e ->
                        it.copy(isCsvBusy = false, csvMessage = "Export failed: ${e.message}")
                    }
                )
            }
        }
    }

    fun importCsv(uri: Uri) {
        if (_uiState.value.isCsvBusy) return
        _uiState.update { it.copy(isCsvBusy = true) }
        viewModelScope.launch {
            val result = importCsvUseCase(uri)
            _uiState.update {
                result.fold(
                    onSuccess = { r ->
                        it.copy(
                            isCsvBusy = false,
                            csvMessage = "Imported ${r.imported}, skipped ${r.skipped}, invalid ${r.invalid}"
                        )
                    },
                    onFailure = { e ->
                        it.copy(isCsvBusy = false, csvMessage = "Import failed: ${e.message}")
                    }
                )
            }
        }
    }

    fun consumeCsvMessage() {
        _uiState.update { it.copy(csvMessage = null) }
    }

    fun consumePendingExport() {
        _uiState.update { it.copy(pendingExport = null) }
    }

    fun loadDemoData() {
        if (_uiState.value.isDemoBusy) return
        _uiState.update { it.copy(isDemoBusy = true) }
        viewModelScope.launch {
            _uiState.update {
                runCatching { demoDataSeeder.seedIfEmpty() }.fold(
                    onSuccess = { inserted ->
                        it.copy(
                            isDemoBusy = false,
                            demoMessage = if (inserted > 0) "Loaded $inserted demo transactions" else "Demo data already loaded"
                        )
                    },
                    onFailure = { e ->
                        it.copy(isDemoBusy = false, demoMessage = "Demo load failed: ${e.message}")
                    }
                )
            }
        }
    }

    fun consumeDemoMessage() {
        _uiState.update { it.copy(demoMessage = null) }
    }
}
