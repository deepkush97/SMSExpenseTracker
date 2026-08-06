package com.smsexpensetracker.ui.screens.categorize

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smsexpensetracker.MainActivity
import com.smsexpensetracker.core.AppState
import com.smsexpensetracker.ui.TestTags
import com.smsexpensetracker.util.skipToMain
import com.smsexpensetracker.util.tapNavItem
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategorizeSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        AppState.reset(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun categorizeEmpty_rendersEmptyState() {
        composeRule.skipToMain()
        composeRule.tapNavItem("Categorize")
        composeRule.onNodeWithTag(TestTags.EMPTY_STATE).assertExists()
        composeRule.onNodeWithText("No transactions yet").assertExists()
    }
}
