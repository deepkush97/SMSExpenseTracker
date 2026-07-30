# Navigation, Theme & App Shell — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the "Hello World!" app with a Material 3 themed app with bottom navigation (4 tabs: Dashboard, Transactions, Parser Test, Settings) and stub screens.

**Architecture:** Single `MainActivity` hosts a `Scaffold` with bottom `NavigationBar`. `NavHost` switches between 4 screen composables. Each screen has a stub ViewModel. Theme colors updated for finance app (green credits, red debits).

**Tech Stack:** Jetpack Compose, Material 3, Navigation Compose 2.9.8, Hilt

**Status:** The theme files (`Color.kt`, `Theme.kt`, `Type.kt`) exist with default Compose template values. `MainActivity.kt` shows "Hello World!" with no navigation. No ViewModels exist.

---

### Task 1: Update Theme Colors for Finance App

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: none
- Produces: `SMSExpenseTrackerTheme` composable (already exists, signature stays the same)

- [ ] **Step 1: Replace Color.kt with finance-themed palette**

Replace the default Purple/Pink colors with a blue-primary, green-secondary (credits), red-error (debits) palette:

```kotlin
package com.smsexpensetracker.ui.theme

import androidx.compose.ui.graphics.Color

val Blue40 = Color(0xFF1A56DB)
val Blue80 = Color(0xFFB3CCF5)
val Green40 = Color(0xFF16A34A)
val Green80 = Color(0xFFA8E6B0)
val Red40 = Color(0xFFDC2626)
val Red80 = Color(0xFFFCA5A5)
val Amber40 = Color(0xFFD97706)
val Amber80 = Color(0xFFFCD34D)
val Gray40 = Color(0xFF6B7280)
val Gray80 = Color(0xFFD1D5DB)
val SurfaceLight = Color(0xFFF8FAFB)
val SurfaceDark = Color(0xFF1C1C1E)
```

- [ ] **Step 2: Update Theme.kt to use new colors**

Replace the existing `DarkColorScheme` and `LightColorScheme`:

```kotlin
private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    secondary = Green40,
    onSecondary = Color.White,
    secondaryContainer = Green80,
    error = Red40,
    onError = Color.White,
    errorContainer = Red80,
    surface = SurfaceLight,
    onSurface = Color(0xFF1C1C1E),
    background = SurfaceLight,
    onBackground = Color(0xFF1C1C1E),
    outline = Gray80
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Color(0xFF003A9A),
    primaryContainer = Color(0xFF0046B5),
    secondary = Green80,
    onSecondary = Color(0xFF003D1A),
    secondaryContainer = Color(0xFF005B26),
    error = Red80,
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    surface = SurfaceDark,
    onSurface = Color(0xFFE5E5E5),
    background = SurfaceDark,
    onBackground = Color(0xFFE5E5E5),
    outline = Gray80
)
```

- [ ] **Step 3: Run build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/theme/Color.kt app/src/main/java/com/smsexpensetracker/ui/theme/Theme.kt
git commit -m "feat(theme): update M3 colors for finance app (blue/green/red)"
```

---

### Task 2: Create Placeholder Screens

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardScreen.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsScreen.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserScreen.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `SMSExpenseTrackerTheme` (from Task 1)
- Produces: 4 `@Composable` screen functions, each taking `Modifier` and returning `Unit`

- [ ] **Step 1: Create DashboardScreen.kt**

```kotlin
package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Dashboard — coming soon")
    }
}
```

- [ ] **Step 2: Create TransactionsScreen.kt**

Same pattern — `Box` with centered `Text("Transactions — coming soon")`

- [ ] **Step 3: Create ParserScreen.kt**

Same pattern — centered `Text("Parser Test — coming soon")`

- [ ] **Step 4: Create SettingsScreen.kt**

Same pattern — centered `Text("Settings — coming soon")`

- [ ] **Step 5: Run build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/
git commit -m "feat(ui): add placeholder screens for all 4 tabs"
```

---

### Task 3: Add Bottom Navigation + NavHost

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/navigation/BottomNavItem.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/MainActivity.kt`

**Interfaces:**
- Consumes: `DashboardScreen`, `TransactionsScreen`, `ParserScreen`, `SettingsScreen` (from Task 2), `SMSExpenseTrackerTheme` (from Task 1)
- Produces: Working `NavHost` with bottom navigation bar, `MainActivity` hosts it

- [ ] **Step 1: Create BottomNavItem.kt**

```kotlin
package com.smsexpensetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Dashboard : BottomNavItem("dashboard", "Dashboard", Icons.Default.Home)
    data object Transactions : BottomNavItem("transactions", "Transactions", Icons.Default.List)
    data object Parser : BottomNavItem("parser", "Parser", Icons.Default.Build)
    data object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)

    companion object {
        val items = listOf(Dashboard, Transactions, Parser, Settings)
    }
}
```

- [ ] **Step 2: Create NavGraph.kt**

```kotlin
package com.smsexpensetracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.smsexpensetracker.ui.screens.dashboard.DashboardScreen
import com.smsexpensetracker.ui.screens.parser.ParserScreen
import com.smsexpensetracker.ui.screens.settings.SettingsScreen
import com.smsexpensetracker.ui.screens.transactions.TransactionsScreen

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = BottomNavItem.Dashboard.route, modifier = modifier) {
        composable(BottomNavItem.Dashboard.route) { DashboardScreen() }
        composable(BottomNavItem.Transactions.route) { TransactionsScreen() }
        composable(BottomNavItem.Parser.route) { ParserScreen() }
        composable(BottomNavItem.Settings.route) { SettingsScreen() }
    }
}
```

- [ ] **Step 3: Update MainActivity.kt**

Replace the entire file:

```kotlin
package com.smsexpensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smsexpensetracker.ui.navigation.AppNavHost
import com.smsexpensetracker.ui.navigation.BottomNavItem
import com.smsexpensetracker.ui.theme.SMSExpenseTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SMSExpenseTrackerTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            BottomNavItem.items.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    AppNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/navigation/ app/src/main/java/com/smsexpensetracker/MainActivity.kt
git commit -m "feat(nav): add bottom navigation with 4 tabs"
```

---

### Task 4: Create EmptyState Composable

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/components/EmptyState.kt`

**Interfaces:**
- Consumes: none
- Produces: `@Composable fun EmptyState(icon: ImageVector, title: String, subtitle: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier)`

- [ ] **Step 1: Create EmptyState.kt**

```kotlin
package com.smsexpensetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
```

- [ ] **Step 2: Run build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/components/EmptyState.kt
git commit -m "feat(ui): add EmptyState composable"
```

---

### Task 5: Create Stub ViewModels

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardViewModel.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionsViewModel.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserViewModel.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsViewModel.kt`

**Interfaces:**
- Consumes: Hilt (each ViewModel is `@HiltViewModel` with `@Inject constructor`)
- Produces: 4 ViewModels with `UiState` subclasses, injectable via Hilt

- [ ] **Step 1: Create DashboardViewModel.kt**

```kotlin
package com.smsexpensetracker.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor() : ViewModel()
```

- [ ] **Step 2: Create TransactionsViewModel.kt**

Same pattern — empty `@HiltViewModel` class.

- [ ] **Step 3: Create ParserViewModel.kt**

Same pattern.

- [ ] **Step 4: Create SettingsViewModel.kt**

Same pattern.

- [ ] **Step 5: Run build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/*/ViewModel*.kt
git commit -m "feat(viewmodel): add Hilt ViewModel stubs for all screens"
```

---

### Task 6: Update TODO.md

**Files:**
- Modify: `TODO.md`

- [ ] **Step 1: Mark Task 9 as `[x]` and sub-items as complete**

```markdown
### [x] 9. Navigation, Theme & Infrastructure
- [x] Implement Material 3 theme (finance colors)
- [x] Implement NavHost with bottom navigation: Dashboard, Transactions, Parser Test, Settings
- [x] Implement `MainActivity` with `@AndroidEntryPoint`, Scaffold with bottom bar
- [x] Implement `EmptyState` composable (icon, title, subtitle, action button)
- [ ] Implement `ErrorBanner`, `ErrorSnackbar`, `ErrorDialog` composables
- [x] Implement `DashboardViewModel`, `TransactionsViewModel`, `ParserViewModel`, `SettingsViewModel` stubs
- [ ] **Verify:** App launches, shows bottom nav, navigation between tabs works
```

Leave `ErrorBanner`/`ErrorSnackbar`/`ErrorDialog` as pending (built as-needed).

- [ ] **Step 2: Commit**

```bash
git add TODO.md
git commit -m "docs: update TODO after navigation + theme + shell"
```
