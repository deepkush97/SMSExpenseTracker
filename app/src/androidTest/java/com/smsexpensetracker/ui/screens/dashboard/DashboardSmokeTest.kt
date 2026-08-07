package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
class DashboardSmokeTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(ResetRule())
        .around(composeRule)

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
            composeRule.onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag(TestTags.GET_STARTED_CARD).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.GET_STARTED_CARD).assertExists()
    }

    @Test
    fun dashboardWithData_showsSummaryAndNoCard() {
        // load demo data first via the onboarding path
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Try with demo data").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(TestTags.GET_STARTED_CARD).fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Total Received").assertExists()
        composeRule.onNodeWithTag(TestTags.GET_STARTED_CARD).assertDoesNotExist()
    }

    @Test
    fun dismissX_hidesCardForSession() {
        skipAndNavigateToDashboard() // Skip, then dismiss the card
        composeRule.onNodeWithContentDescription("Dismiss").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.GET_STARTED_CARD).assertDoesNotExist()
    }

    private fun skipAndNavigateToDashboard() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
