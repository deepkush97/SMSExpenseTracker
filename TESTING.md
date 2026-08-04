# Manual QA Checklist — SMS Expense Tracker

A tap-through test plan. Every item is **Action → Expected result**. Work top to bottom on a fresh install. A fresh install starts with an **empty** transaction list — nothing is auto-seeded. Demo data is opt-in via Settings → Data → **Load demo data** (see §12).

> **Reference numbers:** [U] = covered by an automated unit test today; [M] = manual-only (no unit test). Not every row is marked — the ones that are flag coverage where it matters.

## 0. Prerequisites

- [ ] An Android emulator (API 28+) is running.
- [ ] Build + install the debug APK: `./gradlew installDebug` (or run from Android Studio).
- [ ] Nowhere below requires SMS permission except the Sync section (§5).

---

## 1. First Launch & Navigation

- [ ] Fresh install → app opens on **Dashboard**, NOT an onboarding screen.  → Dashboard opens with an **empty** transaction list (totals at 0, no demo rows).
- [ ] Bottom bar shows four pill tabs in order: **Dashboard → Transactions → Parser → Settings**. → Highlights the active tab; hides on sub-screens.
- [ ] Tap each bottom tab. → Each opens its screen; Dashboard is the start destination.
- [ ] From Settings, open **Logs / Categories / Banks & Rules**; from Transactions tap the **+ (FAB)**. → Each opens a sub-screen with a back arrow; bottom bar is hidden there; back returns to the parent.

---

## 2. Dashboard

> These rows assume data is present — load demo data first (Settings → Data → **Load demo data**, §12).

- [ ] Look at the two summary cards. → "Total Spent" and "Total Received" show animated rupee totals (demo data ≠ 0).
- [ ] Look at the three charts. → "Spending by Bank" (bar), "Monthly Trend" (line), "Category Breakdown" (pie + legend) all render rather than showing "No … data yet".
- [ ] Tap a recent-transaction row. → **Known behavior: nothing happens** (the tap is a stub `{}` no-op in `NavGraph.kt:30`). Not a bug you caused — just know it does nothing.
- [ ] Tap **"View All Transactions"**. → Navigates to the Transactions tab.

---

## 3. Theme

- [ ] Settings → Appearance → select **Light**, **Dark**, **AMOLED**, then **System**. → App recolors immediately; AMOLED gives a pure-black background.
- [ ] Kill and relaunch the app. → The chosen theme persists (stored in DataStore).

---

## 4. Transactions List

- [ ] Tap the Transactions tab. → List of transactions grouped under **Today / Yesterday / <dd MMM yyyy>** headers, newest first, scoped to the current month.
- [ ] Use the **month arrows** on the monthly overview card. → Navigates by month; the *next* arrow is disabled when you're already in the current (or a future) month.
- [ ] Type in the **search bar**. → Filters by description as you type; a clear (×) button appears; clearing restores the list.
- [ ] Toggle the **All / Credit / Debit** chips and the **Bank dropdown**. → List filters accordingly; a filtered-out result set shows "No results" (no action button when filtered).
- [ ] Tap a transaction row. → A **bottom sheet** opens: read-only Amount/Type/Bank/Date/Description.
- [ ] In the sheet, tap the **Category dropdown** and pick a category, then close the sheet. → The change is saved (it appears in the Dashboard category breakdown shortly after).

---

## 5. Sync (SMS)

- [ ] On Transactions, tap the **refresh (Sync) icon**. [M]
  - First time (no permission): → prompts for SMS access. If you pick "Not now", a snackbar says SMS access is needed, offering **Open Settings**.
  - After granting: → sync runs automatically; the icon shows a spinner, then a snackbar "**Scanned X, added Y, unparsed Z**".
- [ ] Re-run the sync twice more. → Second run on identical messages reports **added Y = 0** (idempotent — dedup by SHA-256 of the SMS body).
- [ ] **Emulator test batch** — with SMS permission granted, run `scripts/push_test_sms.sh` (14 real SMS: 7 HDFC, 3 ICICI, 1 DCB, 3 Pluxee), then sync again. [M]
  → Expect: **all 14 messages parse and are added** — 7 HDFC (incl. CC UPI Debit, CC Refund, NetBanking), 3 ICICI (incl. IMPS Credit), 3 Pluxee, 1 DCB — with **0 unparsed**.
- [ ] Verify **date grouping** of newly synced items. → All synced transactions appear under **Today** — the app sets the transaction date to today, not the SMS's own date (by design). The SMS's real timestamp is stored but not surfaced in the list.
- [ ] Settings → **Logs** → open **File Logs**. → `parse_failures.txt` is empty (nothing fails to parse); `unparsed_sms.txt` is empty (the `UNPARSED` tag is never written).
- [ ] Turn off SMS permission in system settings, tap sync. → Snackbar again asks for access with an **Open Settings** shortcut; you can keep using the app (manual entry still works) without permission.

---

## 6. Manual Entry (Add Transaction)

- [ ] Transactions tab → **+ FAB**. → Opens the "Add Transaction" screen.
- [ ] Leave **Amount** blank / type letters → Save. → "Amount is required" / "Enter a valid amount".
- [ ] Type `0` → Save. → "Amount must be greater than zero".
- [ ] Leave **Payee** blank → Save. → "Payee is required". (Over 200 chars → "Payee must be 200 characters or fewer".)
- [ ] Fill a valid **Amount** (e.g. `500.25`), pick **DEBIT/CREDIT**, a **Date** (date picker), **Account**, **Payee**, optional **Reference**, optional **Category** → Save. → Snackbar "Transaction saved"; the form resets (account kept); the row appears in the Transactions list.
- [ ] Add the same amount/payee twice by hand. → **Both are kept** — manual entries have no dedup (expected).

---

## 7. Rule / Bank Management

### Banks
- [ ] Settings → **Banks & Rules**. → Lists the 6 seeded banks (HDFC, ICICI, SBI, Axis, Pluxee, DCB) with sender IDs and transaction counts.
- [ ] **+** add a new bank (name + sender). → Save is only enabled when name ≤30 chars, isn't a duplicate, and sender is non-empty; the sender is stored **uppercased**.
- [ ] Tap a bank row. → Opens the bank detail screen.
- [ ] Try to delete a bank **that has transactions** → "Cannot delete — N transactions use this bank" *(cannot delete)*.
- [ ] Try to delete a bank with **no transactions** → confirm dialog warn; confirms and removes it and its rules.

### Rules
- [ ] In a bank detail, tap **+** to add a rule → opens the Rule Editor.
- [ ] Read the **"How it works & examples"** expandable → it explains `{amount}` / `{description}` / `{name}` anchors and shows 3 example templates.
- [ ] **Pattern (template) testing** — paste a real sample SMS and a template pattern from the examples, then **Test**. → Green "Matches" card with the extracted Amount + Description.
- [ ] Test a pattern with **no `{amount}`** → a validation error under the field ("Pattern must include an {amount} placeholder") and **Test is disabled**.
- [ ] Test a pattern that doesn't match the sample → red "No match for this SMS" card.
- [ ] **Save** gate: Save is disabled until the description is valid, the pattern is valid, **and** you have a successful match. → Fill all three → Save enabled → tap → the rule appears in the bank's rule list with its active **switch ON**.
- [ ] Toggle a rule's active **switch** OFF → relabel + re-run sync → messages matching only that rule stop being parsed.
- [ ] Edit a rule that uses a **legacy regex** pattern (a user-added regex rule) → the editor accepts it (templates and legacy regex both work).
- [ ] Delete a rule → confirm dialog "No longer used to parse transactions."

---

## 8. Categories

- [ ] Settings → Categories. → Lists 14 seeded categories; the **default** ones have a "Default" chip and **no delete icon**.
- [ ] **+** add a category (name + color swatch + icon) → Save enabled only when the name is valid and not a duplicate. Row appears with the chosen color/icon.
- [ ] Tap a (non-default) row → edit dialog; Save updates it.
- [ ] Delete a non-default category → confirm dialog warns "Transactions in this category will become uncategorized".
- [ ] Deleting / editing is not allowed for default categories.

---

## 9. CSV Import / Export

- [ ] Settings → **Export CSV**. → Writes `exports/transactions_<ts>.csv`, opens the Android **share sheet**; a snackbar says "Exported N transactions".
- [ ] Export once, then **Import CSV** and pick that same file. → "Imported N, skipped M, invalid 0" (all rows already dedup so they are skipped).
- [ ] Import a spreadsheet with a wrong/missing header row. → error "Import failed: …invalid header…" (import requires an exact header).
- [ ] Import a file with a row pointing to a non-existent bankId or categoryId. → that row is counted as **invalid** (skip), and the valid rows still import.

---

## 10. Logs

- [ ] Settings → Logs. → Four file-log cards (Error, Parse failures, Unparsed SMS, Crash) + a scrolling parse-log list from the DB.
- [ ] An empty log file → shows "(empty)".
- [ ] **Share** a card → system share sheet with the log content.
- [ ] **Clear** a card → confirm dialog ("This cannot be undone."); Clear empties it.

---

## 11. Unparsed SMS Review

> Requires SMS permission and at least one SMS that fails to parse. To produce failures reliably: `scripts/push_test_sms.sh`, then temporarily disable a seeded rule (or sync before the 14-rule seed shipped) so some SMS don't match.

- [ ] Settings → **Unparsed SMS** (row sits directly above **Logs**). → Screen opens with the **Failed** filter selected.
- [ ] Each card shows the **full SMS body** (monospace, wrapped), sender, bank name (if the sender matches a bank), a red **FAILED** badge, "Failed N×", and the last attempt time.
- [ ] Duplicate bodies appear **once** with a count (e.g. same SMS pushed twice → "Failed 2x").
- [ ] Toggle the **All | Failed** chips. → Failed shows deduped cards; All shows raw parse-log rows (one per log entry).
- [ ] An SMS whose sender matches no bank → "Fix" is **disabled** and the hint "No matching bank — add it in Banks & Rules first." shows.
- [ ] Tap **Fix** on a failing SMS → the **Rule Editor** opens with the **Sample SMS** field pre-filled and the bank selected; write a matching template, **Test** → green "Matches", **Save**.
- [ ] Return to the review screen and tap **Re-sync now** → snackbar "Scanned X, added Y, unparsed Z"; the fixed SMS **no longer appears** (old FAILED rows were cleared first).
- [ ] Tap **Re-sync now** with SMS permission revoked → snackbar "Sync failed. Try again."; **Re-sync now** is disabled (spinner) while syncing.

---

## 12. Edge Cases & Known Behavior

- [ ] **Empty state** — clear app data (or delete all transactions) then open Transactions. → "No transactions yet …" with a **Sync SMS** action. Clearing data then relaunching **stays empty** — the app never auto-seeds.
- [ ] Settings → **Data** → **Load demo data**. → Inserts 60 demo transactions (snackbar "Loaded 60 demo transactions"); tapping again reports "Demo data already loaded" (idempotent — no duplicates).
- [ ] **Sync re-runs** are cheap/idempotent because of body-hash dedup. Two identical SMS bodies collide — the second is dropped.
- [ ] **Parser screen "Add as Transaction"** can be tapped repeatedly and will create duplicates (no dedup on that path).

---

## Which are covered by unit tests today? *(summary)*

| Layer | What the 329 tests cover |
|---|---|
| Parser / template | `TemplateCompiler`, `RegexParser` dual-mode dispatch, `ConfidenceScorer`, `TypeInferrer`, `SenderDetector`, 14 real-SMS template rows (plus 1 "No match" row), matching the 14 messages in `push_test_sms.sh` |
| CSV | `CsvCodec` round-trip + robustness, CSV import FK validation, off-main-thread import |
| Data | ParserEngine, SmsReader query, repository dedup, repositories |
| ViewModels | ManualEntry, Dashboard, Transactions, Settings (CSV), RuleEditor template round-trip, UnparsedSmsViewModel |
| Validation | `BankRulesValidation` (bank/rule/category names, template pattern) |
| **Not covered (manual-only)** | Anything touching the Android runtime — `ContentResolver`/Room/the emulator or real SMS: actual sync, SMS permission flow, share sheet, file picker, DataStore theme, and all Compose UI interaction such as chip filters, dialogs, navigation, charts |