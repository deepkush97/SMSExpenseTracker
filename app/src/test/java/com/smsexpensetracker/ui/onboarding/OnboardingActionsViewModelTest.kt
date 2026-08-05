package com.smsexpensetracker.ui.onboarding

import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.core.settings.OnboardingPreferences
import com.smsexpensetracker.data.demo.DemoDataSeeder
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import com.smsexpensetracker.domain.value.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingActionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val demoDataSeeder = mockk<DemoDataSeeder>()
    private val smsSyncUseCase = mockk<SmsSyncUseCase>()
    private val demoDataPreferences = mockk<DemoDataPreferences>()
    private val onboardingPreferences = mockk<OnboardingPreferences>()
    private val demoDataLoadedFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { demoDataPreferences.demoDataLoaded } returns demoDataLoadedFlow
        coEvery { onboardingPreferences.setOnboardingComplete(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        OnboardingActionsViewModel(demoDataSeeder, smsSyncUseCase, demoDataPreferences, onboardingPreferences)

    @Test
    fun `loadDemoData seeds and marks onboarding complete`() = runTest(testDispatcher) {
        coEvery { demoDataSeeder.seedIfEmpty() } returns 60
        val viewModel = viewModel()

        viewModel.loadDemoData()
        advanceUntilIdle()

        coVerify(exactly = 1) { demoDataSeeder.seedIfEmpty() }
        coVerify(exactly = 1) { onboardingPreferences.setOnboardingComplete(true) }
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `loadDemoData marks complete even when seeding fails`() = runTest(testDispatcher) {
        coEvery { demoDataSeeder.seedIfEmpty() } throws RuntimeException("disk full")
        val viewModel = viewModel()

        viewModel.loadDemoData()
        advanceUntilIdle()

        coVerify(exactly = 1) { onboardingPreferences.setOnboardingComplete(true) }
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `sync runs the use case and marks onboarding complete`() = runTest(testDispatcher) {
        coEvery { smsSyncUseCase.sync() } returns SyncResult(scanned = 5, inserted = 3, unparsed = 1)
        val viewModel = viewModel()

        viewModel.sync()
        advanceUntilIdle()

        coVerify(exactly = 1) { smsSyncUseCase.sync() }
        coVerify(exactly = 1) { onboardingPreferences.setOnboardingComplete(true) }
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `sync with demo data present shows barrier and does not run sync`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.sync()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showDemoBarrier)
        coVerify(exactly = 0) { smsSyncUseCase.sync() }
        coVerify(exactly = 0) { onboardingPreferences.setOnboardingComplete(true) }
    }

    @Test
    fun `markComplete sets onboarding complete`() = runTest(testDispatcher) {
        val viewModel = viewModel()

        viewModel.markComplete()
        advanceUntilIdle()

        coVerify(exactly = 1) { onboardingPreferences.setOnboardingComplete(true) }
    }

    @Test
    fun `confirmDeleteDemoData deletes demo and closes barrier`() = runTest(testDispatcher) {
        demoDataLoadedFlow.value = true
        coEvery { demoDataSeeder.deleteDemoData() } returns Unit
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.confirmDeleteDemoData()
        advanceUntilIdle()

        coVerify(exactly = 1) { demoDataSeeder.deleteDemoData() }
        assertFalse(viewModel.uiState.value.showDemoBarrier)
        assertFalse(viewModel.uiState.value.demoLoaded)
    }
}
