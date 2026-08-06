# SMS Expense Tracker

**Offline-first Android app that reads bank SMS, parses them into structured transactions, and shows spending insights — all on-device. No internet, no cloud, no SMS ever leaves your phone.**

Built for Indian banks (HDFC, ICICI, DCB, Pluxee, SBI, Axis). Parsing is regex-based and runs entirely on the device via a transparent, user-viewable rule engine.

## Features

- **Onboarding** — 3-page welcome flow, then straight into the app (or **Try with demo data** / **Sync my SMS**).
- **Dashboard** — animated Total Spent / Total Received cards, spending-by-bank bar chart, monthly credit-vs-debit trend, category breakdown pie chart, and recent transactions.
- **Transactions** — grouped list (Today / Yesterday / date), month navigation, live search, All/Credit/Debit chips, bank filter, and an in-place edit bottom sheet.
- **Bulk Categorize** — dedicated tab that walks uncategorized transactions and assigns categories from a dropdown.
- **Parser Test** — paste any SMS, see the extracted amount/type/description, and a confidence score.
- **Settings** — appearance (Light / Dark / AMOLED / System), bank & SMS-rule management, categories, logs (error / parse failures / unparsed / crash), demo-data controls, and CSV export/import.
- **Manual entry** — add transactions by hand when an SMS doesn't cover it.
- **SMS sync** — scans the inbox, dedupes by SHA-256 body hash, and records what parsed / failed / was skipped.

## Privacy

`READ_SMS` is a **hard-restricted permission** — the app cannot be distributed via the Play Store. Install it by building with Android Studio or `./gradlew installDebug` (sideload). No network permission is declared at all.

## Tech Stack

| Component  | Technology |
| --- | --- |
| Language    | Kotlin 2.4.x |
| UI          | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI          | Hilt |
| Database    | Room + KSP (schema exported to `app/schemas/`) |
| Navigation  | Navigation Compose |
| Async       | Coroutines + Flow |
| Charts      | Vico (Compose M3) |
| Storage     | DataStore Preferences |
| Logging     | Timber |

SDK: **min 28** (Android 9) / **target 36** / **compile 37**.

## Architecture

```
ui (Compose screens) → ViewModel → UseCase → Repository interface
                                                ↓
                        Repository impl → DAO → Room DB
                        Repository impl → SmsReader (ContentResolver)
                        Repository impl → ParserEngine (pure object, regex)
```

```
com.smsexpensetracker
  core/     Room DB, DAOs, parser engine, CSV codec, settings (DataStore)
  data/     Repository implementations, SmsReader, demo seeder, logging
  domain/   Models, repository interfaces, use cases, value objects
  ui/       Compose screens, components, navigation, onboarding, theme
  di/       Hilt modules
```

**Money rule:** every amount is stored as **paisa** (`Long`) — never `Double` or `BigDecimal`. `parsePaisa("100.50")` → `10050L`.

Parsing pipeline: `SenderDetector` (strips TRAI DLT prefixes, e.g. `AD-HDFCBK-S` → `HDFCBK`) → rule lookup → `RegexParser` (capture group 1 = amount, group 2 = description) → `TypeInferrer` (DEBIT/CREDIT) → `ConfidenceScorer`. Full detail in [`SOLUTION_DESIGN.md`](SOLUTION_DESIGN.md).

## Setup & Build

```bash
# Install the debug APK on a running emulator/device (API 28+)
./gradlew installDebug

# Build the APK only
./gradlew assembleDebug
```

The database is seeded on first launch with **6 banks, 14 categories, and 14 SMS rules**.

## Testing

### Unit tests (JVM, no device needed)

407 tests — parser (14 real SMS patterns), CSV, repositories, SmsReader, demo seeder, ViewModels, validation, categorization logic.

```bash
./gradlew testDebugUnitTest
```

Clean + test:

```bash
./gradlew cleanTestDebugUnitTest testDebugUnitTest
```

### Automated UI acceptance (Compose instrumented)

17 instrumented tests cover the real running app: onboarding, dashboard, transactions, settings, categorize, parser, the SMS-permission **grant** path, and a Room migration test.

**On a live emulator/device** (one must be running, API 28+):

```bash
./gradlew connectedDebugAndroidTest
```

**On the Gradle Managed Device** (provisions a headless Pixel 9 Pro, API 35 automatically — ~1.3 GB system image on first run):

```bash
./gradlew pixel9Api35DebugAndroidTest
```

> The SMS-permission **grant** path is automated. The **deny** path (system dialog → in-app **Open Settings** rescue) is manual-only — see [`TESTING.md`](TESTING.md) §5.

### Real-SMS parser checks

Push all 14 real bank SMS to the emulator, then run a sync in the app to verify they parse:

```bash
scripts/push_test_sms.sh
```

## Documentation

| Doc | What's in it |
| --- | --- |
| [`SOLUTION_DESIGN.md`](SOLUTION_DESIGN.md) | Architecture, domain rules, database schema, verified SMS patterns, roadmap |
| [`TESTING.md`](TESTING.md) | Manual QA checklist + automated UI acceptance run instructions |
| [`TODO.md`](TODO.md) | Task tracking / progress |
| [`AGENTS.md`](AGENTS.md) | Agent & mentorship workflow for this repo |
