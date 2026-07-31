package com.smsexpensetracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.smsexpensetracker.ui.screens.dashboard.DashboardScreen
import com.smsexpensetracker.ui.screens.manualentry.ManualEntryScreen
import com.smsexpensetracker.ui.screens.parser.ParserScreen
import com.smsexpensetracker.ui.screens.settings.SettingsScreen
import com.smsexpensetracker.ui.screens.transactions.TransactionsScreen

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Dashboard.route,
        modifier = modifier
    ) {
        composable(BottomNavItem.Dashboard.route) { DashboardScreen() }
        composable(BottomNavItem.Transactions.route) {
            TransactionsScreen(
                onNavigateToManualEntry = { navController.navigate("manual_entry") }
            )
        }
        composable("manual_entry") {
            ManualEntryScreen(onBack = { navController.popBackStack() })
        }
        composable(BottomNavItem.Parser.route) { ParserScreen() }
        composable(BottomNavItem.Settings.route) { SettingsScreen() }
    }
}
