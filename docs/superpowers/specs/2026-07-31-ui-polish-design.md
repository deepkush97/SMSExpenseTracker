# UI Polish & Fixes — Design Spec

## Overview
Follow-up polish pass on top of the expressive UI redesign (`2026-07-31-expressive-ui-redesign.md`, already implemented). Targets the defects and visual gaps left over: default Roboto font, ungrouped amounts, nested card radius on the dashboard, non-themed chart axes, flat shadow-based cards, a plain transaction list, a low-radius search field, and low-contrast category chip colors. Goal: a modern, fast/snappy Material You look (as in Vivi Music) with the existing expressive theme, pill nav, and springs kept intact.

## Design Decisions (approved)
1. **Font:** bundle **Inter** (Regular/Medium/SemiBold/Bold) in `res/font/`; whole app uses it. Offline, no Play Services.
2. **Amounts:** **Indian grouping** (`₹12,34,567.89`) via `NumberFormat.getNumberInstance(Locale("en","IN"))`. Implemented behind one shared `AmountFormatter` util so the grouping choice can later become a Settings toggle.
3. **Transaction list:** **icon-forward rows** shared between the dashboard and the transactions screen (one composable instead of two).
4. **Category donut:** add a **themed legend** (dot + name + amount).
5. **Credit/debit amount color:** credit = soft green tint, debit = default `onSurface`. (Flagged and approved; trivially changeable.)

## Shared Utilities (new `ui/util/` and `ui/components/`)

### `AmountFormatter.kt` (`ui/util/`)
Pure object, no Compose dependencies — unit-testable.
- `formatPaisa(paisa: Long): String` — Indian grouping, 2-digit paise, `₹` prefix. `123456789L` → `₹12,34,567.89`. Negative paisa renders `-₹12,34,567.89`. Move the existing top-level `formatPaisa` out of `SummaryCard.kt` and re-point every caller (SummaryCard, TransactionListItem/Row, TransactionDetailSheet, MonthlySummaryBanner, and any other usages).
- `formatAmountWithSign(paisa: Long): String` — for list rows: credit → `+₹12,345.00`, debit → `-₹12,345.00` (absolute value after sign).
- Internally: `java.text.NumberFormat` for the grouped whole part + manual `.%02d` paise, so output is deterministic and locale-safe regardless of device locale.

### `CategoryColorScheme.kt` (`ui/util/`)
Pure, theme-injectable color math (the "blend" the user asked for). Drives chips, list avatars, and donut dots so all category-colored surfaces match.
- `readableOnColor(categoryColor: Color): Color` — luminance-based contrast: relative luminance > 0.5 → dark text, else light text.
- `categoryChipColors(categoryColor: Color, container: Color): Pair<Color, Color>` — background = `categoryColor` blended over `container` (~18% alpha), foreground = `readableOnColor(categoryColor)`. Blend via `androidx.core.graphics.ColorUtils.blendARGB`.
- Replaces the current `categoryColor.copy(alpha=0.15f)` + raw `categoryColor` text used in chips, and the raw-tint avatar backgrounds.
- Unit tests with known colors (dark category → light text; bright category → dark text).

### Shared row composable (`ui/components/TransactionRow.kt`)
One content-only composable (no own Surface/shape — the caller's Card provides it), used by both the dashboard's recent-transactions card and the transactions list:
- Leading: 40dp circular avatar filled with category color blended over `surfaceContainerHigh`, containing the category's first letter in `readableOnColor`.
- Title: merchant/description, `bodyMedium` medium weight; subtitle: date + time, `bodySmall` `onSurfaceVariant`.
- Trailing: `formatAmountWithSign`, `bodyMedium` SemiBold, credit green / debit `onSurface`.

## Font & Typography
- Download Inter static TTFs (Regular 400, Medium 500, SemiBold 600, Bold 700) — OFL license — into `app/src/main/res/font/` as `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`, `inter_bold.ttf`.
- `Type.kt`: define `val InterFontFamily = FontFamily(Font(R.font.inter_regular, FontWeight.Normal), ...)` and replace `FontFamily.Default` in all 15 M3 text styles. Everything inherits automatically.
- Note in a comment: Inter is bundled (not a downloadable font) for offline reliability.

## Dashboard Fixes (`ui/screens/dashboard/`)
- **Nested radius bug:** dashboard currently wraps each recent transaction in a `Card` (24dp) that contains `TransactionRow` (its own `Surface`, 12dp) → inner radius visible and wrong. Fix: `TransactionRow` becomes content-only (shared composable above); the Card supplies shape + tonal color.
- **Card tonality:** all dashboard cards switch from shadow-flatness (`cardElevation(2.dp)`) to M3 tonal surfaces — `CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow)` with zero/low elevation. `SummaryCard`'s category tint becomes a blend over `surfaceContainerLow` (via the shared color helpers) instead of raw `copy(alpha=0.1f)`.
- **Chart axis theming (BankChart, MonthlyChart):** Vico axes currently use default (dark) label colors. Pass `MaterialTheme.colorScheme.onSurfaceVariant` for labels and `outlineVariant` for axis lines so they follow dark/light/AMOLED.
- **Category donut legend:** themed legend under the donut — color dot (category color), name, and amount in `onSurfaceVariant`; scrollable if many categories. Legend dots reuse the shared category color helper.

## Transactions Screen (`ui/screens/transactions/`)
- **List:** use the shared icon-forward `TransactionRow` on tonal rounded cards (20–24dp), 8–12dp vertical spacing, no shadow. Date group headers ("Today", "Yesterday", then formatted date) as subtle `bodySmall` `onSurfaceVariant` rows between groups. `TransactionListItem.kt` is **removed**; the `LazyColumn` in `TransactionsScreen` owns group headers + Card placement, and the shared `TransactionRow` supplies the content.
- **Search field:** replace the 12dp `OutlinedTextField` with a filled **pill** — `RoundedCornerShape(50)` (or `CircleShape`), `surfaceContainerHigh` fill, no border, leading search icon, trailing clear button. New `TransactionSearchBar.kt` styling.
- **Category filter chips:** keep expressive rounded chips; selected = `primaryContainer`/`onPrimaryContainer` (existing). Text contrast fixed via `CategoryColorScheme` where categories are colored.

## Snappy Feedback
- Interactive rows/cards get spring scale press feedback (0.97x) reusing `AppAnimation` springs already defined. Keep it subtle; charts untouched.

## Testing
- Unit tests (JUnit 4): `AmountFormatterTest` (Indian grouping incl. paise, negatives, signs, edge cases like 0 and single-digit paise) and `CategoryColorSchemeTest` (luminance thresholds, blend outputs).
- All 132 existing tests must still pass after `formatPaisa` relocation and any signature changes.
- Gate: `./gradlew testDebugUnitTest` + `./gradlew assembleDebug`. No lint/typecheck configured.
- Manual smoke test on emulator/device at the end (visual: dark/light/AMOLED, chips, legends).

## Out of Scope
- Downloadable fonts (Play Services), font toggle in Settings.
- Making grouping configurable now — only structured so it *can* be later.
- DB/migration changes; parser changes; new features.
