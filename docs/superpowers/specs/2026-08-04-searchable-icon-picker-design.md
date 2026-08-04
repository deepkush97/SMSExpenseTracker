# Searchable Category Icon Picker — Design

**Date:** 2026-08-04
**Status:** Approved by user

## 1. Problem

When creating or editing a category, the icon picker shows only **14 hardcoded Material icons** (`CATEGORY_ICON_NAMES` in `CategoryIcons.kt`, rendered as a `FlowRow` in `CategoryDialog.kt:101`). There is no way to browse or search the wider icon library, so users are stuck with a tiny fixed set for their category avatars.

## 2. Goal

A **searchable icon picker** in the Add/Edit Category dialog: a search field above a scrollable grid of ~120-150 curated Material icons. Searching filters icons by their name plus curated keywords/aliases. Selecting an icon still stores the icon's **string name** in the DB — the storage contract is unchanged.

## 3. Scope

- **In:** a single-source-of-truth icon catalog (`List<IconEntry>`) replacing `CATEGORY_ICON_NAMES` + the `materialIcon()` `when`; a pure `searchIcons()` function; a search field + scrollable grid in `CategoryDialog`; unit tests for `searchIcons` and `materialIcon`.
- **Out:** no reflection over all ~2000 icons; no runtime image picking; no emoji support; no icon re-ordering or per-icon colors; no DB migration (all 14 existing keys remain in the catalog); no changes to `CategoryRepository`, `CategoryEntity`, or the ViewModel save signature.

## 4. Components

### 4.1 Icon catalog — `CategoryIcons.kt`

Replace `CATEGORY_ICON_NAMES` and the `materialIcon()` `when` with a single catalog:

```kotlin
data class IconEntry(val name: String, val keywords: List<String>, val imageVector: ImageVector)

val CATEGORY_ICONS: List<IconEntry> = listOf(
    IconEntry("restaurant", listOf("food", "eat", "dining"), Icons.Filled.Restaurant),
    IconEntry("shopping_cart", listOf("cart", "buy", "grocery", "store"), Icons.Filled.ShoppingCart),
    // ... ~120-150 entries covering the existing 14 + common spending categories
)

fun materialIcon(name: String): ImageVector =
    CATEGORY_ICONS.find { it.name == name }?.imageVector ?: Icons.Filled.Category

fun searchIcons(query: String): List<IconEntry> {
    val q = query.trim().lowercase().replace("_", "").replace(" ", "")
    if (q.isEmpty()) return CATEGORY_ICONS
    return CATEGORY_ICONS.filter { entry ->
        (entry.name + entry.keywords.joinToString(" "))
            .lowercase()
            .replace("_", "").replace(" ", "")
            .contains(q)
    }
}
```

- `CATEGORY_COLORS` unchanged.
- **Backward compatibility:** all 14 existing icon keys stay in the catalog, so stored categories render exactly as before; the `Category` fallback (`Icons.Filled.Category`) remains the safety net for unknown keys.
- `CategoryDialog`'s default new-category icon becomes `CATEGORY_ICONS.first().name` (still "restaurant" — keep list order stable).

### 4.2 Search field + grid — `CategoryDialog.kt`

- Add `var iconQuery by remember { mutableStateOf("") }`.
- Above the icon grid, an `OutlinedTextField` ("Search icons") with a trailing clear (×) button; matches the pill-search style used on the Transactions screen.
- Replace the `FlowRow` with a `LazyVerticalGrid` (6 columns) inside a fixed-height scrollable container (`Modifier.height(220.dp)`) so the dialog stays compact; `items(searchIcons(iconQuery))` render `Icon(entry.imageVector)`; tapping sets `icon = entry.name`, clears `iconQuery`, and keeps the dialog open (user still presses Save — same as today).
- Selected-icon highlight (primary tint + circle clip) unchanged. Color picker untouched.

### 4.3 Behavior

- Empty query → all ~120-150 icons (scrollable).
- Search matches name + keywords, case-insensitive, underscores and spaces ignored (e.g. "FOOD" → restaurant; "shoppingcart" → shopping_cart).
- Selection still stores the icon **string name** in the DB — the picker is presentation-only.

## 5. Error handling

- `materialIcon` unknown key → `Icons.Filled.Category` fallback (existing behavior, unchanged).
- No-match search → empty grid (no message needed; the user clears or changes the query).

## 6. Known risks / decisions

- **Curated ~120-150, not all ~2000:** chosen for usability (a wall of icons is unusable) and to avoid reflection (ProGuard/R8 renames `Icons.Filled` fields in release, breaking runtime enumeration).
- **Keywords are manual:** each entry carries a small alias list; this doubles the data to maintain but gives natural searches ("food", "dining"). Accepted per user request.
- **Catalog lives in `CategoryIcons.kt`:** a single file may grow to ~150 entries; acceptable — each entry is one line, and it is the sole source of truth. If it grows past ~250 entries, revisit splitting.
- **Grid capped at 220dp:** keeps the dialog compact; the full set is reachable by scrolling.

## 7. Acceptance criteria

1. Add/Edit Category dialog shows a "Search icons" field above a scrollable grid of ~120-150 icons.
2. Typing "food" (or "FOOD") filters to the restaurant entry (and any keyword-matched entries); typing "shoppingcart" finds shopping_cart; empty query shows the full grid.
3. Selecting an icon, then Save, stores that icon's string name; the category row and Transactions/Categorize surfaces render the correct icon.
4. All 14 pre-existing category icons still render unchanged (backward compatible).
5. `./gradlew testDebugUnitTest assembleDebug` green, including new `searchIcons`/`materialIcon` unit tests.
