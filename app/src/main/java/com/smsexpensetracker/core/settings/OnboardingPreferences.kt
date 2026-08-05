package com.smsexpensetracker.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OnboardingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val onboardingCompleteKey = booleanPreferencesKey("onboarding_complete")

    val onboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[onboardingCompleteKey] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { prefs -> prefs[onboardingCompleteKey] = complete }
    }
}
