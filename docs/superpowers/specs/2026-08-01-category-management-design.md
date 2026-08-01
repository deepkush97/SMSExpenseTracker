# Category Management — Design Spec

**Date:** 2026-08-01
**Status:** Approved
**Task reference:** TODO.md Task 14 (Settings Screen), first sub-feature

## 1. Overview

Users can create, edit, and delete expense categories from the Settings screen. This is the first slice of Task 14 — category management only. Bank/SMS-rule management, sync controls, CSV, log viewer, and about-section refinements remain future work.

Categories are shown across the app as colored chips/avatars (TransactionRow, MonthlyOverviewCard, Dashboard donut legend). The `icon` string field exists on `CategoryEntity` but is **not rendered anywhere yet**; this feature lets users pick an icon, and it renders in the category management UI only.

## 2. Goals

- Full CRUD for user categories (seeded + user-created).
- Guard seeded/default categories from deletion.
- Validate name (non-blank, max 30 chars, case-insensitively unique).
- Color + icon pickers in the add/edit dialog.
- Reachable via a "Categories" row in SettingsScreen that navigates to a new screen.

## 3. Non-Goals

- Rendering category icons in TransactionRow, ManualEntry, TransactionDetailSheet, or Dashboard (deferred to a later UI task).
- Bulk reassignment of transactions when a category is deleted (FK `SET_NULL` handles it).
- Category reordering or merging.
- Bank/SMS-rule management, sync controls, CSV, log viewer (rest of Task 14).

## 4. Data & Delete Semantics

`CategoryEntity`: `id`, `name`, `icon` (String, material icon name), `color` (Int ARGB), `isDefault` (Boolean).

Foreign-key behavior already in place (no schema change):
- `transactions.categoryId` → `onDelete = SET_NULL`: deleting a category leaves its transactions uncategorized.
- `user_category_rules.categoryId` → `onDelete = CASCADE`: deleting a category removes its auto-label rules.

`isDefault = true` marks the 14 seeded categories. **Deleting a seeded category is not allowed** — enforced in the UI (delete affordance disabled) and guarded in the ViewModel (`if (category.isDefault) return`). Editing seeded categories (name/color/icon) is allowed and does not change `isDefault`.

## 5. Repository Layer

Add to `CategoryRepository` interface:

```kotlin
suspend fun insert(category: Category): Long
suspend fun update(category: Category)
suspend fun delete(category: Category)
```

Implement in `CategoryRepositoryImpl` by delegating to the existing `CategoryDao.insert/update/delete` and mapping to/from `CategoryEntity`. `CategoryDao` already has all three methods — no DAO changes needed.

## 6. ViewModel

New file `ui/screens/categories/CategoryManagementViewModel.kt`:

- `@HiltViewModel`, injects `CategoryRepository`.
- `val categories: StateFlow<List<Category>>` — `getAllCategories()` mapped, `stateIn(viewModelScope, WhileSubscribed(5000), emptyList())`.
- `fun addCategory(name: String, icon: String, color: Int)` → `insert(Category(id=0, name, icon, color, isDefault=false))`.
- `fun updateCategory(category: Category)` → `update(category)`.
- `fun deleteCategory(category: Category)` → guard `if (category.isDefault) return`; then `delete(category)`.

## 7. Screen

New package `ui/screens/categories/`:

### `CategoryManagementScreen.kt`
- `Scaffold` with `TopAppBar` (title "Categories", back arrow → `onBack`) and FAB `+` (add).
- `LazyColumn` of rows: circular avatar (icon on `category.color` background), name, "Default" badge on `isDefault`, trailing delete icon (disabled/invisible for `isDefault`).
- Tap row → edit dialog. FAB → add dialog. Delete icon → confirmation dialog.

### `CategoryDialog.kt`
- `AlertDialog` for add and edit (shared composable).
- Name `OutlinedTextField` — error when blank, >30 chars, or non-unique (case-insensitive vs. other categories, excluding self when editing).
- Color grid: 12 preset swatches (the seeded palette — see §9). Selected swatch ringed.
- Icon grid: 14 material icons (seeded names — see §10). Selected icon tinted.
- Save enabled only when valid.

### `CategoryDeleteDialog.kt`
- Confirmation: "Delete {name}?" + body warning: "Transactions in this category will become uncategorized."
- Confirm → `viewModel.deleteCategory(...)`; dismiss → cancel.

## 8. Navigation

- `NavGraph.kt`: add `composable("categories") { CategoryManagementScreen(onBack = { navController.popBackStack() }) }`.
- `SettingsScreen.kt`: add a "Categories" section row (below Appearance, above About). Reuse the `ThemeModeRow` visual pattern (icon + label + chevron) but not the radio; clicking navigates. `SettingsScreen` gains an `onNavigateToCategories: () -> Unit = {}` parameter; `NavGraph` passes `{ navController.navigate("categories") }`.

## 9. Color Palette (12 presets)

The seeded colors, deduplicated to unique values:

| Name | Int value |
|------|-----------|
| teal | -13108 |
| green | -13956304 |
| orange | -48060 |
| red | -13676760 |
| pink | -10496 |
| purple | -16581634 |
| red-ish | -14513374 |
| blue-grey | -12664161 |
| blue | -4880347 |
| brown | -7084816 |
| grey | -7829368 |
| indigo | -10980385 |

(12 entries; the two duplicated seeded pairs — green/-13956304 and purple/-16581634 — collapse to one entry each. `indigo` is added to reach 12.)

## 10. Icon Set (14 Material icons)

Fixed mapping `materialIcon(name: String): ImageVector` in `ui/util/CategoryIcons.kt` with fallback to `Icons.Filled.Category` for unknown names:

restaurant, shopping_cart, local_gas_station, receipt, shopping_bag, movie, local_hospital, directions_car, school, home, flight, payments, trending_up, category.

Mapped to `Icons.Filled.*` equivalents (e.g., `Restaurant`, `ShoppingCart`, `LocalGasStation`, `Receipt`, `ShoppingBag`, `Movie`, `LocalHospital`, `DirectionsCar`, `School`, `Home`, `Flight`, `Payments`, `TrendingUp`, `Category`). All 14 verified present in material-icons-core or material-icons-extended (1.7.8). Unknown/unavailable names resolve to the fallback.

## 11. Testing

`CategoryManagementViewModelTest` (MockK + `runTest`), mirroring existing ViewModel test style:

1. `categories flow emits repository list` — stub `getAllCategories()` flow, collect, assert.
2. `addCategory inserts with isDefault false` — verify `insert` called with `Category(0, name, icon, color, isDefault=false)`.
3. `updateCategory updates` — verify `update` called with the passed category.
4. `deleteCategory deletes non-default` — verify `delete` called.
5. `deleteCategory guards seeded` — `deleteCategory(Category(isDefault=true))` → `verify(exactly=0) { delete(...) }`.
6. Validation lives in the dialog composable (not the ViewModel); uniqueness/blank/length are UI-layer rules tested via a small pure helper `validateCategoryName(name, existing, editingId)` — unit test the helper.

No repository-layer tests required (thin delegation); no screen previews or instrumented tests.

## 12. Files

**New:**
- `domain/repository/CategoryRepository.kt` (modified: +3 methods)
- `data/repository/CategoryRepositoryImpl.kt` (modified)
- `ui/util/CategoryIcons.kt` (icon mapping + validation helper)
- `ui/screens/categories/CategoryManagementViewModel.kt`
- `ui/screens/categories/CategoryManagementScreen.kt`
- `ui/screens/categories/CategoryDialog.kt`
- `ui/screens/categories/CategoryDeleteDialog.kt`
- `app/src/test/.../ui/screens/categories/CategoryManagementViewModelTest.kt`
- `app/src/test/.../ui/util/CategoryValidationTest.kt`

**Modified:**
- `ui/navigation/NavGraph.kt` (route)
- `ui/screens/settings/SettingsScreen.kt` (row + nav param)
- `TODO.md` (mark Settings sub-features)

## 13. Verification

- `./gradlew testDebugUnitTest assembleDebug` — all tests pass, build succeeds.
- Manual smoke: add/edit/delete a category, confirm delete disabled on seeded categories, confirm list updates live.
