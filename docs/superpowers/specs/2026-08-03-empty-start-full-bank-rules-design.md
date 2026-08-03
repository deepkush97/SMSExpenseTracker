# Empty-Start App + Full Bank SMS Rule Set — Design

**Date:** 2026-08-03
**Status:** Approved by user

## 1. Goal

Two changes:

1. **Start empty.** Remove the automatic demo-data seeding on app launch so a fresh install opens with zero transactions. Demo data remains available as an opt-in Settings action.
2. **Complete bank rule coverage.** Seed SMS-parsing rules (using the `{amount}/{description}` template syntax) for HDFC (7), ICICI (3), Pluxee (3), and a newly added DCB Bank (1), so the real SMS formats the user receives are parsed out of the box. All 14 template patterns are already covered by the 14 real-SMS rows in `RegexParserTest` (verified amounts + descriptions).

## 2. Scope

- **In:** demo-data wiring + Settings toggle; seed banks (add DCB); seed SMS rules (convert 6 existing regex → templates, add 8 new); tests for the toggle and the no-auto-seed change; `TESTING.md` + `TODO.md` updates.
- **Out (by earlier user decision):** no Room migration — new banks/rules reach **fresh installs only** (existing installs keep their current DB). No changes to `ParserEngine`, `RegexParser`, `TemplateCompiler`, `TypeInferrer`, `ConfidenceScorer`, or the sync pipeline. No change to categories.

## 3. Components

### 3.1 Demo data: on-demand only

- `SmsExpenseApp.kt:27` — **remove** the `appScope.launch { demoDataSeeder.seedIfEmpty() }` call. App starts with an empty `transactions` table.
- Keep `DemoDataSeeder.kt`, `DemoTransactionGenerator.kt` (unchanged logic, 60 transactions), and their existing unit tests.
- **New:** a Settings → Data action row **"Load demo data"** that invokes the existing `demoDataSeeder.seedIfEmpty()` and reports the result:
  - inserted 60 → snackbar `Loaded 60 demo transactions`
  - skipped (table already has rows) → snackbar `Demo data already loaded`
- Wire-up: `SettingsViewModel` gains a suspend `loadDemoData()` (inject the existing `@Singleton DemoDataSeeder`); `SettingsScreen` adds the row next to Import/Export CSV. Runs off the main thread (seeder already inserts via Room DAO suspend).

### 3.2 Banks (seed)

`SeedDatabaseCallback.seedBanks` adds a 6th bank:

| id | name | smsSender |
|----|------|-----------|
| 6 | DCB Bank | DCBANK |

(Sender `DCBANK` derived from the TRAI DLT sender `JD-DCBANK-T` used in `push_test_sms.sh`; existing 5 banks unchanged.)

### 3.3 SMS rules (seed) — all templates

`SeedDatabaseCallback.seedSmsRules` is rewritten to insert 14 rules. The 6 existing regex rules are converted to templates; 8 are new. Rule order within the list follows bank id then the order below; first-match-wins cannot mis-match because each template begins with a distinct literal.

**HDFC Bank (id 1) — 7 rules:**

| # | description | pattern (template) |
|---|---|---|
| 1 | HDFC CC Debit | `Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}` |
| 2 | HDFC CC UPI Debit | `Txn Rs.{amount} On HDFC Bank Card {card} At {description} by UPI {upi} On {date}` |
| 3 | HDFC CC Refund | `Alert! Rs. {amount} refunded by {description} on {date} & adjusted against HDFC Bank Credit Card {card}` |
| 4 | HDFC UPI Credit | `Rs.{amount} credited to HDFC Bank A/c {account} on {date} from VPA {description} (UPI` |
| 5 | HDFC e-Mandate | `INR {amount} deducted from HDFC Bank A/C No {account} towards {description} UMRN` |
| 6 | HDFC NetBanking | `Rs. {amount} from A/c {account} to {description} via HDFC Bank NetBanking` |
| 7 | HDFC NEFT Credit | `INR {amount} deposited in HDFC Bank A/c {account} on {date} for NEFT Cr-{description}.Avl bal` |

**ICICI Bank (id 2) — 3 rules:**

| # | description | pattern (template) |
|---|---|---|
| 8 | ICICI UPI Debit | `ICICI Bank Acct {account} debited for Rs {amount} on {date}; {description} credited. UPI` |
| 9 | ICICI UPI Credit | `Acct {account} is credited with Rs {amount} on {date} from {description}. UPI` |
| 10 | ICICI IMPS Credit | `ICICI Bank Account {account} is credited with Rs {amount} on {date} by {description}. IMPS` |

**Pluxee (id 5) — 3 rules:**

| # | description | pattern (template) |
|---|---|---|
| 11 | Pluxee Meal Spend | `Rs. {amount} spent from Pluxee Meal Card wallet, card no.{card} on {date} at {description}. Avl bal` |
| 12 | Pluxee Reversal | `Your Pluxee Card xx{card} has been credited with INR {amount} on {date}as {description}.` |
| 13 | Pluxee Wallet Load | `credited with Rs.{amount} towards{wallet} on {description}. Your` |

**DCB Bank (id 6) — 1 rule:**

| # | description | pattern (template) |
|---|---|---|
| 14 | DCB POS/Ecom Debit | `INR {amount} debited DCB Bank a/c*{card} POS/Ecom txn to {description} on {date}` |

Notes:
- Seed inserts keep explicit rule ids (1–14) as today; each pattern's apostrophes are doubled for the `execSQL` string literal (existing `replace("'", "''")`).
- `isActive` defaults to 1 (active) — unchanged.
- Rule **descriptions** used for the DAO `ORDER BY description ASC` (`SmsRuleDao.kt:30`) are the table's "description" column; these become the sort key in `getAllRules()`. Distinct leading literals make match-order irrelevant, but descriptions are kept unique per rule.

## 4. Behavior changes

- Fresh install: **no transactions** until the user syncs SMS or uses Manual Entry / Load demo data. Empty states on Dashboard/Transactions become the normal first-run experience.
- Fresh install: HDFC, ICICI, Pluxee, and DCB senders all parse via the seeded template rules. The `push_test_sms.sh` DCB + Pluxee messages now **parse successfully** instead of becoming parse failures.
- Existing installs: unchanged (no migration).

## 5. Error handling

- Demo-data load: a failed insert surfaces as an existing-pattern snackbar (`Import/Export failed:` style). No new failure modes expected — `seedIfEmpty` guards on `count() == 0`.

## 6. Testing

- `SettingsViewModelTest`: new test that `loadDemoData()` calls the seeder and surfaces the loaded/already-loaded message. Mock `DemoDataSeeder`.
- `DemoDataSeederTest` / `DemoTransactionGeneratorTest`: unchanged (still valid).
- `SmsExpenseApp` change is a removal of a launch coroutine — no test harness exists for `Application.onCreate`; covered by the emulator smoke test (§8).
- Seed integrity: `SeedDatabaseCallback` has no unit test today and inserts via raw SQL on `onCreate`; the seed rules are exercised indirectly by the existing 14 `RegexParserTest` template rows (same SMS → same templates). No new parser tests required.
- `TESTING.md`: update §1 (remove "auto-seeds ~60 demo rows"), §5 (DCB/Pluxee now parse), §11 (empty-state reachable by default; demo data via Settings), and add the Load-demo-data row.
- `TODO.md`: mark demo-data task as on-demand toggle; note full rule coverage under SMS rule management.

## 7. Known risks / decisions

- **Fresh installs only** (user decision): no migration; existing devices must reinstall to get DCB + new rules.
- **No regex→template migration at runtime:** the rewrite lives only in `SeedDatabaseCallback` (`onCreate`), so it affects new installs only. Consistent with §2.
- **Pluxee Reversal literal** uses the canonical `{date}as {description}` form (no space between `{date}` and `as`) — this is the exact pattern already proven in `RegexParserTest` against the real SMS (`...22:41:31as a reversal...`).

## 8. Acceptance criteria

1. Fresh install (clear app data) opens with an empty transaction list — no auto-seeded rows.
2. Settings → Data shows **Load demo data**; tapping loads 60 rows on an empty DB, reports "already loaded" on a second tap.
3. Running `push_test_sms.sh` then syncing on a fresh install parses all 14 messages — **0 unparsed**. That is: 7 HDFC (incl. the 3 new formats CC UPI Debit, CC Refund, NetBanking), 3 ICICI (incl. IMPS Credit), 3 Pluxee, 1 DCB. Previously DCB + Pluxee (4 messages) were parse failures.
4. Rule Editor shows all 14 seeded rules as editable templates under their banks.
5. `./gradlew testDebugUnitTest assembleDebug` green.
