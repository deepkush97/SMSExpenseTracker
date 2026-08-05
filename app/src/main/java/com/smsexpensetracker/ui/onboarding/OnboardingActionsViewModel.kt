package com.smsexpensetracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.core.settings.OnboardingPreferences
import com.smsexpensetracker.data.demo.DemoDataSeeder
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingActionsUiState(
    val isBusy: Boolean = false,
    val showDemoBarrier: Boolean = false,
    val demoLoaded: Boolean = false
)

@HiltViewModel
class OnboardingActionsViewModel @Inject constructor(
    private val demoDataSeeder: DemoDataSeeder,
    private val smsSyncUseCase: SmsSyncUseCase,
    private val demoDataPreferences: DemoDataPreferences,
    private val onboardingPreferences: OnboardingPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingActionsUiState())
    val uiState: StateFlow<OnboardingActionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            demoDataPreferences.demoDataLoaded.collect { loaded ->
                _uiState.update { it.copy(demoLoaded = loaded) }
            }
        }
    }

    fun loadDemoData() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                demoDataSeeder.seedIfEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // retry is available from the onboarding flow or Dashboard card
            } finally {
                onboardingPreferences.setOnboardingComplete(true)
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun sync() {
        if (_uiState.value.isBusy) return
        if (_uiState.value.demoLoaded) {
            _uiState.update { it.copy(showDemoBarrier = true) }
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                smsSyncUseCase.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // retry is available from the onboarding flow or Dashboard card
            } finally {
                onboardingPreferences.setOnboardingComplete(true)
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun markComplete() {
        viewModelScope.launch {
            onboardingPreferences.setOnboardingComplete(true)
        }
    }

    fun dismissDemoBarrier() = _uiState.update { it.copy(showDemoBarrier = false) }

    fun confirmDeleteDemoData() {
        viewModelScope.launch {
            demoDataSeeder.deleteDemoData()
            _uiState.update { it.copy(showDemoBarrier = false, demoLoaded = false) }
        }
    }
}
