package com.smsexpensetracker.util

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smsexpensetracker.ui.TestTags

fun ComposeTestRule.tapNavItem(label: String) {
    onNodeWithTag(TestTags.BOTTOM_NAV).assertExists()
    onNodeWithText(label, useUnmergedTree = true).performClick()
    waitForIdle()
}
