package com.smsexpensetracker.ui.screens.parser

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smsexpensetracker.MainActivity
import com.smsexpensetracker.core.AppState
import com.smsexpensetracker.util.skipToMain
import com.smsexpensetracker.util.tapNavItem
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParserSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun parser_rendersHeaderAndFields() {
        composeRule.skipToMain()
        composeRule.tapNavItem("Parser")
        composeRule.onNodeWithText("Parser Test").assertExists()
        composeRule.onNodeWithText("SMS body").assertExists() // OutlinedTextField label
        composeRule.onNodeWithText("Sender ID").assertExists()
    }
}
