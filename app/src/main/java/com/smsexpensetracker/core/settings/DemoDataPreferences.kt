package com.smsexpensetracker.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DemoDataPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val demoDataLoadedKey = booleanPreferencesKey("demo_data_loaded")

    val demoDataLoaded: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[demoDataLoadedKey] ?: false
    }

    suspend fun setDemoDataLoaded(loaded: Boolean) {
        dataStore.edit { prefs -> prefs[demoDataLoadedKey] = loaded }
    }
}
