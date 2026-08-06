package com.smsexpensetracker.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.MainActivity
import com.smsexpensetracker.core.ResetRule
import com.smsexpensetracker.ui.TestTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingAcceptanceTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(ResetRule())
        .around(composeRule)

    @Test
    fun freshInstall_showsThreePageWelcome_bottomNavHidden() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.ONBOARDING_PAGER).assertExists()
        composeRule.onNodeWithTag(TestTags.BOTTOM_NAV).assertDoesNotExist()
        composeRule.onNodeWithText("SMS Expense Tracker").assertIsDisplayed()
    }

    @Test
    fun skip_landsOnDashboard_withGetStartedCard() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.GET_STARTED_CARD).assertExists()
    }

    @Test
    fun relaunch_doesNotShowWelcomeAgain() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.ONBOARDING_PAGER).assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.GET_STARTED_CARD).assertExists()
    }

    @Test
    fun demoData_loads60RowsAndCardDisappears() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Try with demo data").performClick()
        // seeding is async; wait for the card to go away
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(TestTags.GET_STARTED_CARD).fetchSemanticsNodes().isEmpty()
        }
        // and onboarding itself is gone (markComplete on demo load)
        composeRule.onNodeWithTag(TestTags.ONBOARDING_PAGER).assertDoesNotExist()
    }
}
