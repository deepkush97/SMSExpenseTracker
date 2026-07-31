package com.smsexpensetracker.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.smsexpensetracker.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ThemePreferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun createPreferences(): ThemePreferences {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
            produceFile = { tmp.newFile("test.preferences_pb") }
        )
        return ThemePreferences(dataStore)
    }

    @Test
    fun `defaults to SYSTEM`() = runTest {
        assertEquals(ThemeMode.SYSTEM, createPreferences().themeMode.first())
    }

    @Test
    fun `round trips a written mode`() = runTest {
        val prefs = createPreferences()
        prefs.setThemeMode(ThemeMode.AMOLED)
        assertEquals(ThemeMode.AMOLED, prefs.themeMode.first())
    }
}
