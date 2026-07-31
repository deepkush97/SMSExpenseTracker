package com.smsexpensetracker.ui.screens.settings

import com.smsexpensetracker.core.settings.ThemePreferences
import com.smsexpensetracker.ui.theme.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val themePreferences = mockk<ThemePreferences>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exposes persisted theme mode`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.DARK)
        val viewModel = SettingsViewModel(themePreferences)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        job.cancel()
    }

    @Test
    fun `change persists the selected mode`() = runTest(testDispatcher) {
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { themePreferences.setThemeMode(any()) } returns Unit
        val viewModel = SettingsViewModel(themePreferences)
        viewModel.onThemeModeChange(ThemeMode.AMOLED)
        advanceUntilIdle()
        coVerify { themePreferences.setThemeMode(ThemeMode.AMOLED) }
    }
}
