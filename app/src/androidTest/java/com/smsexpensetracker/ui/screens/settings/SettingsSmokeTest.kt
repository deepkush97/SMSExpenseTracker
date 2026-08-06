package com.smsexpensetracker.ui.screens.settings

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
class SettingsSmokeTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(ResetRule())
        .around(composeRule)

    @Test
    fun settings_rendersSections() {
        composeRule.skipToMain()
        composeRule.tapNavItem("Settings")
        composeRule.onNodeWithText("Appearance").assertExists()
        composeRule.onNodeWithText("Data").assertExists()
        composeRule.onNodeWithText("About").performScrollTo()
        composeRule.onNodeWithText("About").assertExists()
    }
}
