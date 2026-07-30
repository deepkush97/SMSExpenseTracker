# Expressive UI Redesign — Design Spec

## Overview
Full UI redesign inspired by Material3 Expressive theming (as seen in Vivi Music). Swap static colors for dynamic color + seed-based palette using `materialKolor`, adopt `MaterialExpressiveTheme` with `MotionScheme.expressive()`, replace standard `NavigationBar` with a pill-shaped floating nav bar, and apply spring animations + 24dp rounded corners throughout.

## Theme Architecture

### Dependency Changes
| Current | New |
|---------|-----|
| Compose BOM `2026.06.01` | **Remove BOM**, use explicit versions |
| material3 from BOM → 1.4.0 | material3 `1.5.0-alpha24` |
| No opt-in | `-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi` |
| No materialKolor | `com.materialkolor:material-kolor:4.1.1` |

Explicit Compose versions (from BOM `2026.07.00` stable libs):
- `compose-ui = "1.7.8"`
- `compose-foundation = "1.7.8"`
- `compose-animation = "1.7.8"`
- `compose-runtime = "1.7.8"`
- `activity-compose = "1.13.0"`
- All other deps unchanged

### Theme.kt — Expressive Wrapper
```kotlin
@Composable
fun SMSExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    seedColor: Color = Color(0xFF1A56DB),  // branded blue
    content: @Composable () -> Unit
)
```

- Android 12+: use `dynamicDarkColorScheme`/`dynamicLightColorScheme` (system wallpaper)
- Pre-Android 12 or non-default seed: use `materialKolor`'s `rememberDynamicColorScheme()`
- `MaterialExpressiveTheme(colorScheme, typography, motionScheme = MotionScheme.expressive())`
- Pure black option for OLED dark mode

### Type.kt — Full Typography Scale
Replace current skeleton (only `bodyLarge`) with complete M3 type scale matching Vivi's pattern:
- All 15 text styles defined (displayLarge → labelSmall)
- `FontFamily.Default` for now (future: Google Sans / Outfit as downloadable fonts)
- `lineHeight` and `letterSpacing` per M3 2025 spec

### Shapes
Define custom `AppShapes` object in a new `Shape.kt`:
```kotlin
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
```

### Animation Defaults
```kotlin
object AppAnimation {
    val spring = spring(
        dampingRatio = 0.6f,
        stiffness = 400f
    )
    val softSpring = spring(
        dampingRatio = 0.7f,
        stiffness = 300f
    )
}
```

## Navigation — Pill Floating Nav Bar
Replace `NavigationBar` with a custom pill-shaped `FloatingNavigationBar`:

```
┌──────────────────────────────────────┐
│                                      │
│              [Content Area]          │
│                                      │
│ ┌──────────────────────────────────┐ │
│ │  🏠  📋  🔧  ⚙️              │ │  ← pill container, 24dp radius
│ └──────────────────────────────────┘ │
│                                      │
└──────────────────────────────────────┘
```

- Container: `Surface` with `RoundedCornerShape(24.dp)`, horizontal padding 16dp, bottom 16dp
- Each item: `NavigationBarItem` or custom icon+label with spring scale animation on selected
- Background: `surfaceContainer` with blur (if Haze library) or solid
- Elevation: 4dp, `AmbientShadow` + `SpotShadow`

## Component Polish

### Cards (All Screens)
- Shape: `MaterialTheme.shapes.large` (24dp) — replaces default 12dp
- Elevation: 1dp → 2dp
- Spring scale press animation (0.97x on press)

### SummaryCard (Dashboard)
- Shape: `MaterialTheme.shapes.large`
- Icon tint and amount text: use `seedColor` tones from palette
- Spring animation `animateIntAsState` → `spring()` instead of `tween()`

### Charts (Dashboard — BankChart, MonthlyChart, CategoryChart)
- Wrapped in `Card` with `large` shape
- No internal animation changes (Vico handles its own)

### TransactionListItem / TransactionRow
- Shape: `MaterialTheme.shapes.small` (12dp) or `medium` (16dp) for the row surface
- Category chip: `RoundedCornerShape(8.dp)` → `extraSmall`
- Spring `animateFloatAsState` instead of no animation

### TransactionDetailSheet (Bottom Sheet)
- `ModalBottomSheet` → expressive shape (24dp top corners)
- Smooth spring drag handling

### TransactionFilterChips
- `FilterChip` → expressive styling (rounded 20dp)
- Selected chip color: `primaryContainer` / `onPrimaryContainer`

### EmptyState
- Icon size 64dp → 72dp with spring alpha animation
- Container shape alignment

### MonthlySummaryBanner
- Background: `surfaceVariant` with 16dp rounded corners
- Navigation arrows with spring press

## Implementation Phases

### Phase 1: Theme Foundation
1. Update `libs.versions.toml` — remove BOM, add explicit versions + materialKolor
2. Add opt-in compiler arg to `app/build.gradle.kts`
3. Rewrite `Color.kt` — seed color, `materialKolor` imports
4. Rewrite `Type.kt` — full typography scale
5. Create `Shape.kt` — `AppShapes`
6. Create `Animation.kt` — spring defaults
7. Rewrite `Theme.kt` — `MaterialExpressiveTheme` + `MotionScheme.expressive()`

### Phase 2: Navigation
8. Replace `NavigationBar` with pill-shaped `FloatingNavigationBar`
9. Spring animation on tab selection

### Phase 3: Component Polish
10. `SummaryCard` — large shape, spring animation, tonal bg
11. `TransactionListItem` — medium shape, spring press
12. `TransactionRow` — medium shape, spring press
13. `TransactionFilterChips` — expressive chip shape
14. `TransactionDetailSheet` — expressive bottom sheet shape
15. `MonthlySummaryBanner` — rounded surface, spring arrows
16. `EmptyState` — expressive icon size/animation
17. Cards in `DashboardScreen` — large shape override

### Phase 4: Build & Verify
18. `./gradlew assembleDebug` — build pass
19. `./gradlew testDebugUnitTest` — all tests pass
20. Visual smoke test on emulator
