package com.smsexpensetracker.ui.onboarding

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smsexpensetracker.MainActivity
import com.smsexpensetracker.core.AppState
import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.di.SettingsModule
import com.smsexpensetracker.ui.TestTags
import com.smsexpensetracker.util.TestPermissions
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingPermissionTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(object : TestWatcher() {
            override fun starting(description: Description) {
                val ctx = InstrumentationRegistry.getInstrumentation().targetContext
                AppState.reset(ctx)
                runBlocking {
                    DemoDataPreferences(SettingsModule.provideSettingsDataStore(ctx.applicationContext))
                        .setDemoDataLoaded(false)
                }
            }
        })
        .around(composeRule)

    @Test fun syncGranted_flowProceeds_grantsSmsPermission() {
        TestPermissions.grant(InstrumentationRegistry.getInstrumentation().targetContext)
        composeRule.waitForIdle()
        // go to page 3
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sync my SMS").performClick()
        // since granted, sync proceeds; the app ends on main (nav shown)
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.ONBOARDING_PAGER).assertDoesNotExist()
    }
}
