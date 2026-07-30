# Expressive UI Redesign — Implementation Plan

## Overview
20 tasks across 4 phases: Theme Foundation → Navigation → Component Polish → Build & Verify.

## Files to Modify
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/smsexpensetracker/ui/theme/Color.kt`
- `app/src/main/java/com/smsexpensetracker/ui/theme/Theme.kt`
- `app/src/main/java/com/smsexpensetracker/ui/theme/Type.kt`
- (new) `app/src/main/java/com/smsexpensetracker/ui/theme/Shape.kt`
- (new) `app/src/main/java/com/smsexpensetracker/ui/theme/Animation.kt`
- `app/src/main/java/com/smsexpensetracker/MainActivity.kt`
- `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/SummaryCard.kt`
- `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/TransactionRow.kt`
- `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionListItem.kt`
- `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionFilterChips.kt`
- `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/TransactionDetailSheet.kt`
- `app/src/main/java/com/smsexpensetracker/ui/screens/transactions/MonthlySummaryBanner.kt`
- `app/src/main/java/com/smsexpensetracker/ui/components/EmptyState.kt`

## Phase 1 — Theme Foundation (Tasks 1–7)

### Task 1: Update `gradle/libs.versions.toml`
- Remove `composeBom = "2026.06.01"` entry
- Add explicit versions: `compose = "1.7.8"`, `material3 = "1.5.0-alpha24"`, `materialKolor = "4.1.1"`
- Add `material3` library entry pointing to explicit version (not BOM)
- Remove BOM reference from `androidx-compose-*` entries (they inherit version via group:name:version)
- Add `materialKolor` library entry

### Task 2: Update `app/build.gradle.kts`
- Add opt-in: `-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi`
- Replace BOM platform dependency with explicit versioned deps
- Add `materialKolor` implementation dependency
- Keep Compose compiler plugin (Kotlin 2.4.10 handles it)

### Task 3: Rewrite `Color.kt`
```kotlin
package com.smsexpensetracker.ui.theme

import androidx.compose.ui.graphics.Color

val SeedColor = Color(0xFF1A56DB)  // branded blue
val DefaultThemeColor = SeedColor
```

### Task 4: Rewrite `Type.kt`
Full M3 typography scale with all 15 text styles, using `FontFamily.Default`.
Match Vivi's `getTypography()` pattern exactly (displayLarge → labelSmall with correct font sizes, line heights, letter spacing per M3 2025 spec).

### Task 5: Create `Shape.kt`
```kotlin
package com.smsexpensetracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
```

### Task 6: Create `Animation.kt`
```kotlin
package com.smsexpensetracker.ui.theme

import androidx.compose.animation.core.spring

object AppAnimation {
    val spring = spring(dampingRatio = 0.6f, stiffness = 400f)
    val softSpring = spring(dampingRatio = 0.7f, stiffness = 300f)
}
```

### Task 7: Rewrite `Theme.kt`
- Accept `darkTheme`, `pureBlack`, `seedColor` params
- Android 12+: `dynamicDarkColorScheme`/`dynamicLightColorScheme`
- Pre-12 or custom seed: `rememberDynamicColorScheme()` from materialKolor (tonalspot style)
- `MaterialExpressiveTheme(colorScheme, typography, shapes = AppShapes, motionScheme = MotionScheme.expressive())`
- Pure black override for OLED

## Phase 2 — Navigation (Task 8–9)

### Task 8: Pill Floating Navigation in `MainActivity.kt`
- Replace `NavigationBar` with custom pill layout:
  - `Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, bottom = 16.dp))`
  - Inner `Surface(RoundedCornerShape(24.dp), elevation = 4.dp, color = MaterialTheme.colorScheme.surfaceContainer)`
  - `Row` with `NavigationBarItem` for each tab
- Remove standard `NavigationBar`, keep `Scaffold` with `bottomBar`

### Task 9: Spring Tab Animation
- Add `animateFloatAsState` on each `NavigationBarItem` for scale transform (1f selected, 0.92f unselected → with spring)
- Tinted icon based on selection state

## Phase 3 — Component Polish (Tasks 10–17)

### Task 10: `SummaryCard.kt`
- Use `MaterialTheme.shapes.large` (remove default shape)
- Replace `tween(600)` with `spring()` in `animateIntAsState`
- Background: `colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))` (keep)
- Medium title size, bold weight, tinted amount

### Task 11: `TransactionListItem.kt`
- Remove `RoundedCornerShape` inline usage → rely on theme shapes
- Wrap entire row in `Surface(shape = MaterialTheme.shapes.small)` with spring press animation
- Category chip: use `MaterialTheme.shapes.extraSmall`

### Task 12: `TransactionRow.kt` (Dashboard)
- Wrap in `Surface(shape = MaterialTheme.shapes.small)` 
- Spring press animation via `interactionSource`

### Task 13: `TransactionFilterChips.kt`
- Update `FilterChip` shapes — they already use MaterialTheme shapes by default
- Ensure selected chip uses `primaryContainer` colors
- Bank dropdown `OutlinedTextField` with expressive shape

### Task 14: `TransactionDetailSheet.kt`
- `ModalBottomSheet` already has `sheetState` — verify it picks up theme shapes
- Content padding: add top 24dp rounded via shape

### Task 15: `MonthlySummaryBanner.kt`
- Wrap in `Surface(shape = MaterialTheme.shapes.medium)` with `surfaceVariant` color
- Month arrows: spring scale animation

### Task 16: `EmptyState.kt`
- Icon size 64dp → 72dp
- Container uses theme shapes
- Spring alpha animation on appear (optional)

### Task 17: `DashboardScreen.kt` Card Overrides
- Individual cards (`BankChart`, `MonthlyChart`, `CategoryChart`, transaction `Card`) — ensure they use `CardDefaults` with `shape = MaterialTheme.shapes.large`

## Phase 4 — Build & Verify (Tasks 18–20)

### Task 18: `./gradlew assembleDebug`
Resolve any compilation issues (missing imports, API changes between material3 1.4.0 → 1.5.0-alpha, etc.)

### Task 19: `./gradlew cleanTestDebugUnitTest testDebugUnitTest`
All existing tests must pass with no regressions.

### Task 20: Visual smoke test on emulator
- Launch app, verify no crashes
- Verify pill nav bar renders correctly
- Verify cards have larger rounded corners
- Toggle dark mode
- Verify spring animations feel smooth

## Execution Order
Sequential within phases; phases are sequential (Phase 1 → 2 → 3 → 4).
Tasks within each phase can be parallel where independent.
Phase 4 requires all prior phases complete.

## Rollback
If build fails after Phase 1, revert `libs.versions.toml` + `build.gradle.kts` to previous commit and retry with manual version pinning.
