package com.smsexpensetracker.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingPreferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun createPreferences(): OnboardingPreferences {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
            produceFile = { tmp.newFile("test.preferences_pb") }
        )
        return OnboardingPreferences(dataStore)
    }

    @Test
    fun `defaults to false`() = runTest {
        assertFalse(createPreferences().onboardingComplete.first())
    }

    @Test
    fun `round trips a written true value`() = runTest {
        val prefs = createPreferences()
        prefs.setOnboardingComplete(true)
        assertTrue(prefs.onboardingComplete.first())
    }

    @Test
    fun `round trips a written false value`() = runTest {
        val prefs = createPreferences()
        prefs.setOnboardingComplete(true)
        prefs.setOnboardingComplete(false)
        assertFalse(prefs.onboardingComplete.first())
    }
}
