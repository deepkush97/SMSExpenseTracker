# SMS Expense Tracker — Solution Design

> **Version:** 3.0  
> **Date:** July 2026  
> **Status:** Living — reflects code as built. Roadmap items marked clearly.

---

## 1. Executive Summary

**SMS Expense Tracker** is an offline-first Android app that reads bank SMS, parses them into structured transactions, and displays insights via Compose dashboards. No internet required. Parsing is regex-based and fully on-device.

### Key Differentiators

- **100% Offline** — no network calls
- **Privacy-First** — raw SMS never leaves the device
- **Indian Bank Focused** — 4 banks verified from real SMS (HDFC, ICICI, DCB, Pluxee), 5 seeded
- **Transparent Parsing** — users can view and test regex rules

---

## 2. Project Goals & Constraints

### 2.1 Functional Goals

| ID  | Goal                                                            | Priority |
| --- | --------------------------------------------------------------- | -------- |
| F1  | Read bank SMS, extract amount + type + payee                    | P0       |
| F2  | Display transactions in a filterable, searchable list           | P0       |
| F3  | Allow manual categorization of transactions                     | P0       |
| F4  | Dashboard: per-bank credit vs debit bar chart                   | P0       |
| F5  | Dashboard: monthly credit vs debit trend                        | P0       |
| F6  | Dashboard: category-wise spending breakdown                     | P0       |
| F7  | Parser test interface                                           | P1       |
| F8  | Room database for local storage                                 | P0       |
| F9  | CSV export/import for backup                                    | P1       |

### 2.2 Non-Functional Constraints

| Constraint      | Detail                                                              |
| --------------- | ------------------------------------------------------------------- |
| **Zero Internet** | No network. All parsing on-device via Kotlin regex.               |
| **Min SDK 28**  | Android 9.0+                                                        |
| **Target SDK**  | 36 (Android 16)                                                     |
| **Compile SDK** | 37                                                                  |
| **Privacy**     | `READ_SMS` is hard-restricted — APK/sideload distribution           |
| **Performance** | Parsing <50ms per message                                           |

### 2.3 Testability

- Emulators lack real SMS → use `adb emu sms send` or `scripts/push_test_sms.sh`
- Parser Test screen lets users paste SMS and see extracted fields
- All logic tested via JUnit 4 + MockK (no Robolectric)

---

## 3. High-Level Architecture

### 3.1 Layers

```
Presentation (Compose) → ViewModel → Use Case → Repository Interface
                                                       ↓
                                              Repository Impl → DAO → Room DB
                                              Repository Impl → SmsReader (ContentResolver)
                                              Repository Impl → ParserEngine (pure object)
```

### 3.2 Package Structure

```
com.smsexpensetracker
  core/
    database/       — Room entities, DAOs, Converters, SeedCallback, SmsExpenseDatabase
    parser/         — SenderDetector, RegexParser, TypeInferrer, ConfidenceScorer, ParserEngine
  data/
    repository/     — Repository implementations (each with unit tests)
    sms/            — SmsReader, SmsMessage
  domain/
    model/          — Transaction, Bank, SmsRule, Category, ParseLog, SyncMeta, etc.
    repository/     — Repository interfaces
    usecase/        — ParseSmsUseCase, GetTransactionsUseCase, etc. (stubs)
    value/          — ParsedResult, ConfidenceScore, SenderId, SyncProgress, SyncRange
  ui/
    theme/          — Color, Theme, Type (Compose theme only, no screens yet)
  di/               — DatabaseModule (Hilt)
```

---

## 4. Technology Stack

| Component          | Technology                               |
| ------------------ | ---------------------------------------- |
| Language           | Kotlin 2.4.10                            |
| UI                 | Jetpack Compose + Material 3             |
| Architecture       | MVVM + Clean Architecture                |
| DI                 | Hilt 2.60.1                              |
| Database           | Room 2.8.4 + KSP                         |
| Navigation         | Navigation Compose 2.9.8                 |
| Async              | Coroutines 1.11.0 + Flow                 |
| Charts             | Vico 3.2.3 (Compose M3)                  |
| Logging            | Timber 5.0.1                             |
| Testing            | JUnit 4, MockK 1.14.11, coroutines-test  |

---

## 5. Database Design

### 5.1 Entity Relationship

```
BANK
  id (PK), name, smsSender

SMS_RULE → BANK (FK: bankId)
  id (PK), bankId (FK), pattern, description

TRANSACTION → BANK (FK: bankId)
             → CATEGORY (FK: categoryId, nullable)
  id (PK), bankId, amount (paisa), type (CREDIT/DEBIT), description,
  transactionDate, categoryId?, rawSms, smsTimestamp, createdAt

CATEGORY
  id (PK), name, icon, color, isDefault

PARSE_LOG
  id (PK), smsBody, smsSender, parsedAt, status (SUCCESS/FAILED/SKIPPED), errorMessage?

SYNC_META
  id (PK, always 1), lastSyncTimeStamp, lastSmsId?

TRANSACTION_LABEL (entity exists, planned)
  id (PK), transactionId (FK), categoryId (FK), isAutoAssigned, createdAt

USER_CATEGORY_RULE (entity exists, planned)
  id (PK), categoryId (FK), keyword, merchantPattern, priority, isActive
```

### 5.2 Money

All amounts as **paisa** (`Long`). `parsePaisa("100.50")` → `10050L`. Never `Double` or `BigDecimal`.

### 5.3 Seed Data

On first `onCreate`, database is seeded with:
- 5 banks: HDFC Bank, ICICI Bank, DCB Bank, Pluxee, SBI
- 14 categories: Food & Dining, Groceries, Fuel, Bills & Utilities, Shopping, Transport, Entertainment, Health, Education, Salary, Rent, EMI/Loans, Investment, Others
- 6 SMS rules for the seeded banks

### 5.4 Schema Export

Room schema exported to `app/schemas/` (committed to git). Version 1.

---

## 6. SMS Parsing Pipeline

### 6.1 Components

| Component          | Role                                                              |
| ------------------ | ----------------------------------------------------------------- |
| `SenderDetector`   | Strips TRAI DLT prefix/suffix from sender ID (`AD-HDFCBK-S` → `HDFCBK`) |
| `RegexParser`      | Applies a regex pattern, extracts amount (group 1) and description (group 2) |
| `TypeInferrer`     | Scans SMS body for keywords → DEBIT or CREDIT                     |
| `ConfidenceScorer` | Scores 0.0–1.0 based on matched groups, amount + description presence |
| `ParserEngine`     | Orchestrates: detect sender → iterate rules → parse → infer type → score → return ParsedResult |

### 6.2 Flow

```
Raw SMS + sender → SenderDetector.detect(sender)
    → for each (bankId, pattern) in rules:
        RegexParser.parse(smsBody, pattern, bankId)
        if match: TypeInferrer.infer(smsBody) + ConfidenceScorer.score(...)
        return ParsedResult(amount, type, description, bankId, confidence)
    → no match: ParsedResult(errorMessage, confidence=0)
```

### 6.3 Sender ID Detection

TRAI DLT format: `XY-BANKNAME-SUFFIX` (e.g., `AD-HDFCBK-S`).  
Algorithm: split on `-`, return first segment with ≥3 alphanumeric chars.

| Bank        | Base Pattern | Sender IDs              |
| ----------- | ------------ | ----------------------- |
| HDFC Bank   | `HDFCBK`     | `AD-HDFCBK-S`, etc.     |
| ICICI Bank  | `ICICIT`     | `AD-ICICIT-S`           |
| DCB Bank    | `DCBANK`     | `JD-DCBANK-T`           |
| Pluxee      | `Pluxee`     | `VD-Pluxee-S`, etc.     |
| SBI         | `SBIINB`     | seeded, SMS not tested  |

### 6.4 Transaction Type

Enum: `CREDIT`, `DEBIT` only (in both `domain.model` and `core.database.entity` packages).

### 6.5 Regex Pattern Convention

All patterns use numbered capture groups:
- Group 1 (`([\\d,.]+)`) = amount in rupees (converted to paisa ×100)
- Group 2 (`(.+?)`) = description / payee

---

## 7. Verified SMS Patterns

### 7.1 HDFC Bank (Sender: AD-HDFCBK-S)

**7.1a CC Merchant Debit**
```
Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51...
```
Amount: 483176 | Type: DEBIT | Description: Acme Inc.

**7.1b CC UPI Debit**
```
Txn Rs.25.00\nOn HDFC Bank Card 1111\nAt Q123456789@ybl\nby UPI 620436716168\nOn 23-07...
```
Amount: 2500 | Type: DEBIT | Description: Q123456789@ybl

**7.1c CC Refund**
```
Alert! Rs. 32 refunded by someComp on 20/JUL/2026 & adjusted against HDFC Bank Credit Card 1111...
```
Amount: 3200 | Type: CREDIT | Description: someComp

**7.1d UPI Credit**
```
Credit Alert!\nRs.12000.00 credited to HDFC Bank A/c XX1111 on 18-07-26 from VPA yourupi@addr (UPI 656540994008)
```
Amount: 1200000 | Type: CREDIT | Description: yourupi@addr

**7.1e e-Mandate Debit**
```
PAYMENT ALERT! \nINR 1000.00 deducted from HDFC Bank A/C No 1234 towards Some CORP UMRN: HDFC7011403241000251
```
Amount: 100000 | Type: DEBIT | Description: Some CORP

**7.1f NetBanking Debit**
```
Payment Successful! Rs. 66093.00 from A/c **********1233 to SOMECORP via HDFC Bank NetBanking...
```
Amount: 6609300 | Type: DEBIT | Description: SOMECORP

**7.1g NEFT Credit**
```
Update! INR 1,000.00 deposited in HDFC Bank A/c XX1233 on 31-MAR-26 for NEFT Cr-ICIC0099999-SOMECOMPANY-someName...
```
Amount: 100000 | Type: CREDIT | Description: ICIC0099999-SOMECOMPANY-someName

### 7.2 ICICI Bank (Sender: AD-ICICIT-S)

**7.2a UPI Debit**
```
ICICI Bank Acct XX123 debited for Rs 242.00 on 26-Jul-26; BUS Ticket credited. UPI:003637672623...
```
Amount: 24200 | Type: DEBIT | Description: BUS Ticket

**7.2b UPI Credit**
```
Dear Customer, Acct XX123 is credited with Rs 20.00 on 19-Jul-26 from NPCI BHIM. UPI:103691213332-ICICI Bank.
```
Amount: 2000 | Type: CREDIT | Description: NPCI BHIM

**7.2c IMPS Credit**
```
ICICI Bank Account XX123 is credited with Rs 61,000.00 on 03-Jul-26 by Account linked to mobile number XXXXX01234. IMPS Ref. no. 618441385660.
```
Amount: 6100000 | Type: CREDIT | Description: Account linked to mobile number XXXXX01234

### 7.3 DCB Bank (Sender: JD-DCBANK-T)

**7.3a POS/Ecom Debit**
```
INR 1403.36 debited DCB Bank a/c*1234 POS/Ecom txn to cafe de lar on 19-06-2026 07:59 PM...
```
Amount: 140336 | Type: DEBIT | Description: cafe de lar

### 7.4 Pluxee (Sender: VD-Pluxee-S)

**7.4a Meal Spend Debit**
```
Rs. 546.00 spent from Pluxee  Meal Card wallet, card no.xx4910 on 28-06-2026 21:38:47 at SWIGGY...
```
Amount: 54600 | Type: DEBIT | Description: SWIGGY

**7.4b Reversal Credit**
```
Your Pluxee Card xx4910 has been credited with INR 546.00 on Sun Jun 28 2026 22:41:31as a reversal...
```
Amount: 54600 | Type: CREDIT | Description: Sun Jun 28 2026 22:41:31

**7.4c Wallet Load Credit**
```
Your Pluxee Card has been successfully credited with Rs.2200 towards  Meal Wallet on Thu Sep 05 2024 17:03:06...
```
Amount: 220000 | Type: CREDIT | Description: Thu Sep 05 2024 17:03:06

---

## 8. Category Detection Keywords

| Category              | Keywords/Merchants                                |
| --------------------- | ------------------------------------------------- |
| Food & Dining         | swiggy, zomato, dominos, pizza, food, restaurant  |
| Groceries             | bigbasket, blinkit, zepto, dmart, grocery         |
| Fuel                  | petrol, diesel, hp, bpcl, iocl, shell, fuel       |
| Bills & Utilities     | electricity, water, gas, broadband, jio, airtel    |
| Shopping              | amazon, flipkart, meesho, ajio, myntra            |
| Transport             | uber, ola, rapido, metro, parking, toll           |
| Entertainment         | netflix, prime, hotstar, spotify, movie, cinema   |
| Health                | pharmacy, medical, hospital, doctor, clinic       |
| Education             | school, college, university, course               |
| Salary                | salary, payroll, wage                              |
| Rent                  | rent, house, flat                                  |
| EMI/Loans             | emi, loan, installment, sip                        |

---

## 9. Testing Strategy

- **Unit tests**: JUnit 4 + `@RunWith(Parameterized::class)` for data-driven tests. MockK for mocking. `runTest { }` for coroutines.
- **Repository tests**: mock DAOs, verify entity↔domain mapping, insert/query/delete flow.
- **SmsReader tests**: mock `ContentResolver` + `Cursor`; use `any()` matchers in `every`, explicit `verify` with `coVerify` for suspend.
- **22 parser tests**: 14 SMS patterns + sender detection + confidence scoring + type inference + no-match.
- **Test SMS push**: `scripts/push_test_sms.sh` — pushes all 14 SMS to emulator via `adb emu sms send`.

---

## 10. Roadmap (Not Yet Built)

### 10.1 PermissionManager
Runtime `READ_SMS` permission request with rationale dialog and Settings fallback.

### 10.2 Sync Use Case
`SmsSyncUseCase` orchestrates: `SmsReader.readSms(range)` → debounce(300ms) → chunk(100) → `ParserEngine.parseBatch()` → `TransactionRepository.insertBatch()` → emit progress. Deduplication via SHA-256 `smsBodyHash` with `@Insert(onConflict = IGNORE)`.

### 10.3 Background Sync
`SmsSyncWorker` via WorkManager for periodic background sync with battery constraints.

### 10.4 ParseLog Recording
Every parse attempt records a `ParseLog` entry (SUCCESS/FAILED/SKIPPED).

### 10.5 FileLogger + Backup
Write logs to `filesDir/logs/`. Timber tree for forwarding. CSV export/import for transactions.

### 10.6 Navigation + Theme + UI Screens
- Material 3 theme + NavHost with bottom nav (Dashboard, Transactions, Parser Test, Settings)
- Dashboard: summary cards, bar/line/pie charts (Vico)
- Transaction list: search, filter chips, LazyColumn, detail bottom sheet
- Parser Test: SMS input, parse button, result display, regex rule editor
- Settings: bank/rule/category management, sync controls, CSV buttons

### 10.7 Onboarding
First-launch detection, permission explanation, sync range picker.

### 10.8 TransactionLabel + UserCategoryRule
Entities exist. Need domain models, repos, and auto-categorization engine.

### 10.9 CI/CD
GitHub Actions: lint+test, debug APK, release build with signing.

### 10.10 Migrations
Currently version 1. Future schema changes will add `MIGRATION_1_2` with tests.
