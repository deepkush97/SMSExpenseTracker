package com.smsexpensetracker.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smsexpensetracker.ui.screens.banks.BankDetailScreen
import com.smsexpensetracker.ui.screens.banks.BankManagementScreen
import com.smsexpensetracker.ui.screens.banks.RuleEditorScreen
import com.smsexpensetracker.ui.screens.categories.CategoryManagementScreen
import com.smsexpensetracker.ui.screens.categorize.BulkCategorizeScreen
import com.smsexpensetracker.ui.screens.categorize.CategorizeScreen
import com.smsexpensetracker.ui.screens.dashboard.DashboardScreen
import com.smsexpensetracker.ui.screens.logs.LogViewerScreen
import com.smsexpensetracker.ui.screens.manualentry.ManualEntryScreen
import com.smsexpensetracker.ui.screens.parser.ParserScreen
import com.smsexpensetracker.ui.screens.settings.RuleManagerScreen
import com.smsexpensetracker.ui.screens.settings.SettingsScreen
import com.smsexpensetracker.ui.screens.transactions.TransactionsScreen
import com.smsexpensetracker.ui.screens.unparsed.UnparsedSmsScreen

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Dashboard.route,
        modifier = modifier
    ) {
        composable(BottomNavItem.Dashboard.route) {
            DashboardScreen(
                onNavigateToTransactions = {
                    navController.navigate(BottomNavItem.Transactions.route)
                }
            )
        }
        composable(BottomNavItem.Transactions.route) {
            TransactionsScreen(
                onNavigateToManualEntry = { navController.navigate("manual_entry") }
            )
        }
        composable("manual_entry") {
            ManualEntryScreen(onBack = { navController.popBackStack() })
        }
        composable(BottomNavItem.Categorize.route) {
            CategorizeScreen(
                onBulkCategorize = { navController.navigate("bulk_categorize") }
            )
        }
        composable("bulk_categorize") {
            BulkCategorizeScreen(onBack = { navController.popBackStack() })
        }
        composable(BottomNavItem.Parser.route) { ParserScreen() }
        composable(BottomNavItem.Settings.route) {
            SettingsScreen(
                onNavigateToCategories = { navController.navigate("categories") },
                onNavigateToBanks = { navController.navigate("banks") },
                onNavigateToLogs = { navController.navigate("logs") },
                onNavigateToUnparsedSms = { navController.navigate("unparsed_sms") },
                onNavigateToCategoryRules = { navController.navigate("category_rules") }
            )
        }
        composable("category_rules") {
            RuleManagerScreen(onBack = { navController.popBackStack() })
        }
        composable("logs") {
            LogViewerScreen(onBack = { navController.popBackStack() })
        }
        composable("unparsed_sms") {
            UnparsedSmsScreen(
                onBack = { navController.popBackStack() },
                onFix = { bankId, smsBody ->
                    navController.navigate("banks/$bankId/rules/edit?sampleSms=${Uri.encode(smsBody)}")
                }
            )
        }
        composable("categories") {
            CategoryManagementScreen(onBack = { navController.popBackStack() })
        }
        composable("banks") {
            BankManagementScreen(
                onBack = { navController.popBackStack() },
                onBankClick = { bank -> navController.navigate("banks/${bank.id}") }
            )
        }
        composable(
            route = "banks/{bankId}",
            arguments = listOf(navArgument("bankId") { type = NavType.LongType })
        ) { entry ->
            val bankId = entry.arguments?.getLong("bankId")
            BankDetailScreen(
                onBack = { navController.popBackStack() },
                onAddRule = { bankId?.let { navController.navigate("banks/$it/rules/edit") } },
                onEditRule = { ruleId -> bankId?.let { navController.navigate("banks/$it/rules/edit?ruleId=$ruleId") } }
            )
        }
        composable(
            route = "banks/{bankId}/rules/edit?ruleId={ruleId}&sampleSms={sampleSms}",
            arguments = listOf(
                navArgument("bankId") { type = NavType.LongType },
                navArgument("ruleId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("sampleSms") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            RuleEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}
