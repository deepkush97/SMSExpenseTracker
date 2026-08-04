package com.smsexpensetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Dashboard : BottomNavItem("dashboard", "Dashboard", Icons.Default.Home)
    data object Transactions : BottomNavItem("transactions", "Transactions", Icons.Default.List)
    data object Categorize : BottomNavItem("categorize", "Categorize", Icons.Filled.Sell)
    data object Parser : BottomNavItem("parser", "Parser", Icons.Default.Build)
    data object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)

    companion object {
        val items = listOf(Dashboard, Transactions, Categorize, Parser, Settings)
    }
}
