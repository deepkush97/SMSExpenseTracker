package com.smsexpensetracker.ui.screens.parser

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.MainActivity
import com.smsexpensetracker.core.ResetRule
import com.smsexpensetracker.util.skipToMain
import com.smsexpensetracker.util.tapNavItem
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParserSmokeTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(ResetRule())
        .around(composeRule)

    @Test
    fun parser_rendersHeaderAndFields() {
        composeRule.skipToMain()
        composeRule.tapNavItem("Parser")
        composeRule.onNodeWithText("Parser Test").assertExists()
        composeRule.onNodeWithText("SMS body").assertExists() // OutlinedTextField label
        composeRule.onNodeWithText("Sender ID").assertExists()
    }
}
