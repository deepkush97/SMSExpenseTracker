package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smsexpensetracker.MainActivity
import com.smsexpensetracker.core.AppState
import com.smsexpensetracker.ui.TestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun freshInstall_showsWelcome_notMain() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.ONBOARDING_PAGER).assertExists()
        composeRule.onNodeWithTag(TestTags.BOTTOM_NAV).assertDoesNotExist()
    }

    @Test
    fun skip_leadsToDashboardWithGetStartedCard() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.GET_STARTED_CARD).assertExists()
    }
}
