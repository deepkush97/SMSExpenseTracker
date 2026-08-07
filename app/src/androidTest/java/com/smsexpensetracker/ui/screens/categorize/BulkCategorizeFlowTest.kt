package com.smsexpensetracker.ui.screens.categorize

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smsexpensetracker.MainActivity
import com.smsexpensetracker.core.ResetRule
import com.smsexpensetracker.core.database.SmsExpenseDatabase
import com.smsexpensetracker.core.database.entity.ParseMethod
import com.smsexpensetracker.core.database.entity.TransactionEntity
import com.smsexpensetracker.core.database.entity.TransactionType
import com.smsexpensetracker.core.settings.OnboardingPreferences
import com.smsexpensetracker.di.SettingsModule
import com.smsexpensetracker.ui.TestTags
import com.smsexpensetracker.util.tapNavItem
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class BulkCategorizeFlowTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(ResetRule())
        .around(BulkSeedRule())
        .around(composeRule)

    @Test
    fun bulkCategorize_showsBannerAndAppliesSuggestions() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(TestTags.BOTTOM_NAV).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.tapNavItem("Categorize")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(TestTags.BULK_CATEGORIZE_BANNER).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.BULK_CATEGORIZE_BANNER).assertExists()

        composeRule.onNodeWithTag(TestTags.BULK_CATEGORIZE_BANNER).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Apply").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Apply").assertExists()

        composeRule.onNodeWithText("Apply").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("4 categorized, 0 uncategorized")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("4 categorized, 0 uncategorized").assertExists()
    }

    private class BulkSeedRule : TestWatcher() {
        override fun starting(description: Description) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            runBlocking {
                seedUncategorizedTransactions(context)
                OnboardingPreferences(SettingsModule.provideSettingsDataStore(context.applicationContext))
                    .setOnboardingComplete(true)
            }
        }

        private fun seedUncategorizedTransactions(context: Context) = runBlocking<Unit> {
            val db = SmsExpenseDatabase.getInstance(context.applicationContext)
            val now = LocalDateTime.now()
            val uncategorized = buildList {
                repeat(4) {
                    add(
                        TransactionEntity(
                            bankId = 1,
                            amount = 2500L,
                            type = TransactionType.DEBIT,
                            description = "BigBasket Grocery",
                            transactionDate = now,
                            categoryId = null,
                            rawSms = "",
                            smsTimestamp = 0,
                            parseMethod = ParseMethod.MANUAL
                        )
                    )
                }
            }
            val classified = buildList {
                repeat(3) {
                    add(
                        TransactionEntity(
                            bankId = 1,
                            amount = 1500L,
                            type = TransactionType.DEBIT,
                            description = "BigBasket",
                            transactionDate = now,
                            categoryId = 2,
                            rawSms = "",
                            smsTimestamp = 0,
                            parseMethod = ParseMethod.MANUAL
                        )
                    )
                }
            }
            db.transactionDao().insertAll(uncategorized + classified)
        }
    }
}