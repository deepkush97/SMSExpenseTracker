# Bulk Categorize with Rule Suggestions — Design

**Date:** 2026-08-07
**Status:** Approved (sections 1–3)
**Supersedes:** the deferred "auto-categorization / suggestion engine" scope of
`2026-08-04-demo-data-gate-and-bulk-categorize-design.md`

## Problem
After syncing a real device, a user can have 2500+ SMS transactions with
`categoryId == null`. The existing Categorize tab (per-transaction dropdown) makes
bulk categorization a tedious one-by-one tap. The app already has an
auto-categorizer (`AutoCategoryEngine` + `user_category_rules` table) but it has
**no UI** and only runs during sync — it never touches existing uncategorized rows.

Design goals (from user):
1. **Automate bulk** re-categorization using keyword rules.
2. **Faster manual** pass for whatever remains.
3. Rules are **permanent and reusable** — they drive future syncs too.

---

## Architecture

New pure component in `core/categorize/` (no DI, mirrors `AutoCategoryEngine`):

### `RuleSuggestionEngine`
```
object RuleSuggestionEngine {
    fun suggest(
        uncategorized: List<Transaction>,
        classified: List<Transaction>,
        categories: List<Category>,
        minCount: Int = 3,
        minKeywordLength: Int = 3
    ): List<RuleSuggestion>
}

data class RuleSuggestion(
    val keyword: String,          // canonical (lowercased) token
    val transactionCount: Int,    // occurrences across all txns
    val suggestedCategoryId: Long? // null if no evidence; user picks
)
```

Deterministic algorithm (no ML):
1. Tokenize each `uncategorized` description: lowercase, split on non-alphanumerics,
   drop tokens shorter than `minKeywordLength`.
2. Count each keyword across **all** transactions (uncategorized + classified).
3. Keep keywords appearing >= `minCount` times.
4. Guess category by **majority**: among classified transactions whose description
   contains the keyword, take the most frequent `categoryId`. If none exist, or the
   top frequency is a tie, return `null` (user picks).
5. Emit one `RuleSuggestion` per surviving keyword.

**Conflict guard:** an additional helper `conflicts(suggestions)` flags a pair whose
keywords share a common prefix (e.g. "amazon" and "amazonpay"). These surface a
warning in the UI because `AutoCategoryEngine.matchCategory` is first-match-wins
ordered by rule `id`.

### New / reused repository interactions (no schema change, no DAO change)
- **Reuse:** `CategoryRepository.getRules()`, `insertRule(rule)`,
  `deleteRule(rule)`, `getAllCategories()` — all already exist (some with zero UI
  callers today).
- **Reuse:** `AutoCategoryEngine.matchCategory(description, rules)` — unchanged.
- **Reuse:** `TransactionRepository.updateTransactionCategory(id, categoryId)` for
  per-row reassignment on Apply.

Batch-apply writes one row at a time via `updateTransactionCategory` over the
`categoryId == null` set — an acceptable loop for ~2500 rows. No bulk SQL added.

---

## Screens & UX

### A. New "Bulk Categorize" screen (nav route, entered from the Categorize tab)
The Categorize tab shows an entry banner: **"N uncategorized — Categorize automatically"**
(when `queue` has uncategorized rows). Tapping it opens the bulk flow.

States:
1. **Scanning** — loading indicator while `RuleSuggestionEngine` runs.
2. **Suggestions list** — one row per suggestion:
   - keyword chip + "appears in N transactions";
   - category dropdown (pre-selected with the engine's majority guess, else
     "Pick category" placeholder);
   - an on/off toggle (default ON when a category is picked; OFF otherwise).
3. **Live preview bar** — "Categorizes ~N of M uncategorized", recomputed as rows
   are toggled or assigned.
4. **Apply** — performs the write (see data flow), then shows a summary:
   "X categorized, Y still uncategorized." A button returns to the Categorize tab.
   An **empty state** shows "No uncategorized transactions 🎉".

### B. Faster manual pass (upgrade existing Categorize tab)
- Replace the per-transaction dropdown with a **grid of category chips**; tapping a
  chip assigns the category and **auto-advances** to the next uncategorized row.
- **"Same as previous"** button for repetitive merchants.
- Keep Skip and the "None" (clear category) option.

### C. Rule manager (small, under Settings)
- Lists saved `user_category_rules` as "keyword → category" rows with **delete**.
- Needed so a bad rule created in the bulk flow can be removed cleanly.

---

## Data flow (Apply)

1. For each confirmed suggestion (toggled ON + has a category):
   - dedupe against existing rules; if absent,
     `CategoryRepository.insertRule(UserCategoryRule(pattern=keyword, categoryId))`.
2. Re-read current rules (`getRules().first()`).
3. For every transaction with `categoryId == null`:
   `match = AutoCategoryEngine.matchCategory(description, rules)`;
   if `match != null`, `TransactionRepository.updateTransactionCategory(id, match)`.
4. Refresh flows; the Categorize tab queue re-collects and now shows the smaller
   remaining set.

**Future syncs:** because confirmed rules persist in `user_category_rules`, the
existing `SmsSyncUseCase` auto-categorization keeps applying them to new SMS —
no change needed there.

---

## Edge cases

- **Empty set** → no entry banner; bulk screen shows empty state.
- **Conflicting keywords** (shared prefix) → warning in suggestions list.
- **Suggestion reached in evidence** → stays `null`, excluded from preview, reported
  under "still uncategorized".
- **Duplicate rule** (exact `pattern`+`categoryId` already saved) → skip insert.
- **Concurrent edits during scan** → suggestion generation is read-only; Apply is one
  suspend write. Acceptable.

---

## Testing

Unit (`app/src/test/`):
- `RuleSuggestionEngineTest` (new): grouping by shared keyword; majority-category
  guessing; min-count threshold; min-keyword-length; tokenization edge cases
  (punctuation, lowercase, multi-word merchants); tie → `null`; prefix-conflict
  detection.
- `CategorizeViewModelTest` (extend existing): confirm → `insertRule` called;
  Apply → `updateTransactionCategory` uses fresh rules; uncategorized-only writes;
  dedupe; OFF-no-category excluded.
- Duplicate-rule skip test.

Instrumented (`app/src/androidTest/`): a smoke test driving the Categorize tab →
Bulk screen → Apply against in-memory DB with seeded rules and transactions, then
assert rows are re-categorized.

Build/run: unit `./gradlew testDebugUnitTest`, instrumented
`./gradlew connectedDebugAndroidTest` (or the managed-device variant).

---

## Files
| Path | Change |
|---|---|
| `core/categorize/RuleSuggestionEngine.kt` | New (pure object + `RuleSuggestion`) |
| `core/categorize/AutoCategoryEngine.kt` | Unchanged |
| `ui/screens/categorize/CategorizeViewModel.kt` | Add bulk-suggest state + actions |
| `ui/screens/categorize/BulkCategorizeScreen.kt` | New |
| `ui/screens/categorize/CategorizeScreen.kt` | Entry banner + faster chip grid |
| `ui/screens/settings/SettingsScreen.kt` | Rule-manager section |
| `ui/screens/settings/` (RuleManager) | New small screen/VMs |
| `ui/navigation/` | New routes |
| `test/.../RuleSuggestionEngineTest.kt` | New |
| `test/.../CategorizeViewModelTest.kt` | Extend |
| `androidTest/.../BulkCategorizeFlowTest.kt` | New smoke |

**Non-goals:** no schema bump, no ML/third-party NLP, no changes to
`AutoCategoryEngine` or `SmsSyncUseCase`, no changes to banks/rules parse templates.