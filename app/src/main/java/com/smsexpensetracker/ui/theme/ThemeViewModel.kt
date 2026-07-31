package com.smsexpensetracker.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.settings.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }
}
