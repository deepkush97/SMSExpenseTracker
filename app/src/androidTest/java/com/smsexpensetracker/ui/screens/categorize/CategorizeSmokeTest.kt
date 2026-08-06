package com.smsexpensetracker.ui.screens.categorize

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.MainActivity
import com.smsexpensetracker.core.ResetRule
import com.smsexpensetracker.ui.TestTags
import com.smsexpensetracker.util.skipToMain
import com.smsexpensetracker.util.tapNavItem
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategorizeSmokeTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(ResetRule())
        .around(composeRule)

    @Test
    fun categorizeEmpty_rendersEmptyState() {
        composeRule.skipToMain()
        composeRule.tapNavItem("Categorize")
        composeRule.onNodeWithTag(TestTags.EMPTY_STATE).assertExists()
        composeRule.onNodeWithText("No transactions yet").assertExists()
    }
}
