package com.smsexpensetracker.ui.screens.settings

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
class SettingsSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
    }

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
