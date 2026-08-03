# Template-Based SMS Parsing Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users author SMS parsing rules as readable `{fieldName}` templates (e.g. `Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}`) instead of positional regex groups, while all existing regex rules keep working.

**Architecture:** A new pure `TemplateCompiler` in `core/parser/` turns a template into the same `Regex` machinery the parser already uses — literals are regex-escaped, whitespace runs become `\s+`, `{amount}` becomes a money group, `{description}` and other `{name}` become non-greedy captures. `RegexParser.parse` dispatches: templates via `TemplateCompiler`, everything else via the existing group-1/group-2 path. `ConfidenceScorer` compiles through the same shared path (templates would otherwise crash it). Templates are stored verbatim in `sms_rules.pattern` and shown as-is in the UI; compiled regex is never surfaced.

**Tech Stack:** Kotlin, JUnit 4 (parameterized where data-driven), `kotlinx-coroutines-test`. No schema change.

## Global Constraints

- Package root: `com.smsexpensetracker`. Money is paisa `Long` — never `Double`/`BigDecimal`.
- No code comments unless the user asks for them.
- Build gate: `./gradlew testDebugUnitTest assembleDebug` must be green before each commit. No `lint`/`typecheck` configured.
- JUnit 4 with MockK mocks; `kotlinx-coroutines-test` `runTest { }` for suspend tests; `StandardTestDispatcher` + `Dispatchers.setMain` for ViewModel tests.
- Mode detection: a pattern containing `{letter...}` (regex `\{[a-zA-Z][a-zA-Z0-9]*\}`) is a template; anything else is legacy regex. `\d{4}` is NOT a template.
- Template rules: literals regex-escaped; interior whitespace runs → `\s+`; leading/trailing whitespace dropped; `{amount}` → `(?<amount>[\d,]+(?:\.\d{1,2})?)` (first occurrence only — later ones become anchors); `{description}` → `(?<description>.+?)` with `description2`, `description3`… for repeats; other `{name}` → numbered anchor groups; a terminal placeholder (template ends with it) compiles **greedy** (`.+`); repeated `{description}` captures join with `"; "`; anchor values are discarded.
- Existing test baseline: 271. Task 1 adds 11 → 282; Task 2 adds 16 → 298; Task 3 adds 5 → 303; Task 4 adds 1 → 304.
- Pre-existing uncommitted changes to `DashboardViewModel.kt` and `opencode.json` are NOT part of this plan — never stage or touch them.

---

### Task 1: TemplateCompiler (core)

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/core/parser/TemplateCompiler.kt`
- Create: `app/src/test/java/com/smsexpensetracker/core/parser/TemplateCompilerTest.kt`

**Interfaces:**
- Consumes: `parsePaisa(input: String): Long?` from `core/parser/Paisa.kt`, `RegexMatch(amount, description, bankId, rawSms)` from `core/parser/RegexParser.kt`.
- Produces (for Tasks 2-4): `object TemplateCompiler` with `fun isTemplate(pattern: String): Boolean`, `fun compile(template: String): Regex?`, `fun extract(smsBody: String, template: String, bankId: Long): RegexMatch?`, `fun findPlaceholders(template: String): List<String>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/core/parser/TemplateCompilerTest.kt`:

```kotlin
package com.smsexpensetracker.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateCompilerTest {

    private val sms = "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You?"

    @Test
    fun `isTemplate detects placeholder braces`() {
        assertTrue(TemplateCompiler.isTemplate("Spent Rs.{amount} On HDFC Bank Card {card}"))
        assertFalse(TemplateCompiler.isTemplate("Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4}"))
    }

    @Test
    fun `isTemplate does not treat quantifier braces as template`() {
        assertFalse(TemplateCompiler.isTemplate("Spent \\d{4}"))
    }

    @Test
    fun `compile rejects template without amount`() {
        assertNull(TemplateCompiler.compile("Your Card {card} credited"))
    }

    @Test
    fun `compile rejects malformed template`() {
        assertNull(TemplateCompiler.compile("Rs.{amount} On {date"))
    }

    @Test
    fun `extract parses amount and description`() {
        val result = TemplateCompiler.extract(
            sms,
            "Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}",
            1L
        )
        assertEquals(483176L, result?.amount)
        assertEquals("Acme Inc.", result?.description)
        assertEquals(1L, result?.bankId)
    }

    @Test
    fun `extract matches with flexible whitespace`() {
        val result = TemplateCompiler.extract(
            "Rs. 546.00 spent from Pluxee  Meal Card wallet",
            "Rs. {amount} spent from Pluxee Meal Card wallet",
            4L
        )
        assertEquals(54600L, result?.amount)
    }

    @Test
    fun `extract combines repeated descriptions with semicolon`() {
        val result = TemplateCompiler.extract(
            "100.00 debited at Swiggy ref 1234",
            "{amount} debited at {description} ref {description}",
            2L
        )
        assertEquals(10000L, result?.amount)
        assertEquals("Swiggy; 1234", result?.description)
    }

    @Test
    fun `extract terminal placeholder captures to end`() {
        val result = TemplateCompiler.extract(
            "INR 1000.00 deducted from HDFC Bank A/C No 1234 towards Some CORP UMRN",
            "INR {amount} deducted from HDFC Bank A/C No {account} towards {description}",
            1L
        )
        assertEquals(100000L, result?.amount)
        assertEquals("Some CORP UMRN", result?.description)
    }

    @Test
    fun `extract returns null when amount is not parseable`() {
        assertNull(
            TemplateCompiler.extract("Spent abc On HDFC Bank Card", "Spent {amount} On HDFC Bank Card", 1L)
        )
    }

    @Test
    fun `extract uses first amount occurrence`() {
        val result = TemplateCompiler.extract(
            "Rs. 546.00 and Rs. 999.00",
            "Rs. {amount} and Rs. {amount}",
            1L
        )
        assertEquals(54600L, result?.amount)
    }

    @Test
    fun `findPlaceholders returns names in order`() {
        assertEquals(
            listOf("amount", "card", "description"),
            TemplateCompiler.findPlaceholders("Rs.{amount} Card {card} At {description}")
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.parser.TemplateCompilerTest"`
Expected: COMPILATION FAILURE — `TemplateCompiler` not defined.

- [ ] **Step 3: Implement TemplateCompiler**

Create `app/src/main/java/com/smsexpensetracker/core/parser/TemplateCompiler.kt`:

```kotlin
package com.smsexpensetracker.core.parser

import java.util.regex.PatternSyntaxException

object TemplateCompiler {

    private val PLACEHOLDER = Regex("\\{([a-zA-Z][a-zA-Z0-9]*)\\}")

    fun isTemplate(pattern: String): Boolean = PLACEHOLDER.containsMatchIn(pattern)

    fun findPlaceholders(template: String): List<String> =
        PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toList()

    fun compile(template: String): Regex? {
        val trimmed = template.trim()
        val placeholders = PLACEHOLDER.findAll(trimmed).toList()
        if (placeholders.isEmpty()) return null
        val names = placeholders.map { it.groupValues[1] }
        if ("amount" !in names) return null

        val builder = StringBuilder()
        var last = 0
        var descriptionCount = 0
        var anchorCount = 0
        var amountSeen = false

        placeholders.forEachIndexed { index, match ->
            val name = match.groupValues[1]
            appendLiteral(builder, trimmed.substring(last, match.range.first))
            val isTerminal = index == placeholders.lastIndex
            val group = when {
                name == "amount" && !amountSeen -> {
                    amountSeen = true
                    "(?<amount>[\\d,]+(?:\\.[\\d]{1,2})?)"
                }
                name == "amount" -> anchorGroup(++anchorCount, isTerminal)
                name == "description" -> {
                    descriptionCount++
                    val groupName = if (descriptionCount == 1) "description" else "description$descriptionCount"
                    if (isTerminal) "(?<$groupName>.+)" else "(?<$groupName>.+?)"
                }
                else -> anchorGroup(++anchorCount, isTerminal)
            }
            builder.append(group)
            last = match.range.last + 1
        }
        appendLiteral(builder, trimmed.substring(last))

        return try {
            Regex(builder.toString(), RegexOption.IGNORE_CASE)
        } catch (e: PatternSyntaxException) {
            null
        }
    }

    fun extract(smsBody: String, template: String, bankId: Long): RegexMatch? {
        val compiled = compile(template) ?: return null
        val match = compiled.find(smsBody) ?: return null
        val amount = match.groups["amount"]?.value?.let { parsePaisa(it) } ?: return null
        val descriptionCount = findPlaceholders(template).count { it == "description" }
        val descriptions = (1..descriptionCount).mapNotNull { i ->
            val name = if (i == 1) "description" else "description$i"
            match.groups[name]?.value?.trim()
        }
        return RegexMatch(
            amount = amount,
            description = descriptions.joinToString("; "),
            bankId = bankId,
            rawSms = smsBody
        )
    }

    private fun anchorGroup(count: Int, isTerminal: Boolean): String =
        if (isTerminal) "(?<a$count>.+)" else "(?<a$count>.+?)"

    private fun appendLiteral(builder: StringBuilder, literal: String) {
        var i = 0
        while (i < literal.length) {
            val c = literal[i]
            if (c.isWhitespace()) {
                while (i < literal.length && literal[i].isWhitespace()) i++
                builder.append("\\s+")
            } else {
                builder.append(Regex.escape(c.toString()))
                i++
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.parser.TemplateCompilerTest"`
Expected: PASS, 11 tests, 0 failures.

- [ ] **Step 5: Run the full gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL (271 existing + 11 new = 282).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/parser/TemplateCompiler.kt app/src/test/java/com/smsexpensetracker/core/parser/TemplateCompilerTest.kt
git commit -m "feat(parser): add template compiler for rule patterns"
```

---

### Task 2: Dual-mode dispatch in RegexParser + ConfidenceScorer

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/parser/RegexParser.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/core/parser/ConfidenceScorer.kt`
- Modify: `app/src/test/java/com/smsexpensetracker/core/parser/RegexParserTest.kt`
- Modify: `app/src/test/java/com/smsexpensetracker/core/parser/ConfidenceScorerTest.kt`

**Interfaces:**
- Consumes (from Task 1): `TemplateCompiler.isTemplate(pattern)`, `TemplateCompiler.compile(template)`, `TemplateCompiler.extract(smsBody, template, bankId)`.
- Produces: `RegexParser.parse(smsBody, pattern, bankId)` dispatches template vs legacy; `ConfidenceScorer.score(...)` compiles through the shared path so templates never throw.

- [ ] **Step 1: Write the failing tests**

Add 14 template rows to the end of `RegexParserTest.kt`'s `data()` list (after the existing "No match" row):

```kotlin
            arrayOf(
                "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 2468 to 7308080808",
                "Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}",
                1L, "HDFC CC Debit TPL", 483176L, "Acme Inc."
            ),
            arrayOf(
                "Credit Alert!\nRs.12000.00 credited to HDFC Bank A/c XX1111 on 18-07-26 from VPA yourupi@addr (UPI 656540994008)",
                "Rs.{amount} credited to HDFC Bank A/c {account} on {date} from VPA {description} (UPI",
                1L, "HDFC UPI Credit TPL", 1200000L, "yourupi@addr"
            ),
            arrayOf(
                "PAYMENT ALERT! \nINR 1000.00 deducted from HDFC Bank A/C No 1234 towards Some CORP UMRN: HDFC7011403241000251",
                "INR {amount} deducted from HDFC Bank A/C No {account} towards {description} UMRN",
                1L, "HDFC e-Mandate TPL", 100000L, "Some CORP"
            ),
            arrayOf(
                "Update! INR 1,000.00 deposited in HDFC Bank A/c XX1233 on 31-MAR-26 for NEFT Cr-ICIC0099999-SOMECOMPANY-someName.Avl bal INR 1,01,000.95. Cheque deposits in A/C are subject to clearing",
                "INR {amount} deposited in HDFC Bank A/c {account} on {date} for NEFT Cr-{description}.Avl bal",
                1L, "HDFC NEFT Credit TPL", 100000L, "ICIC0099999-SOMECOMPANY-someName"
            ),
            arrayOf(
                "ICICI Bank Acct XX123 debited for Rs 242.00 on 26-Jul-26; BUS Ticket credited. UPI:003637672623. Call 18002662 for dispute. SMS BLOCK 796 to 9215676766.",
                "ICICI Bank Acct {account} debited for Rs {amount} on {date}; {description} credited. UPI",
                2L, "ICICI UPI Debit TPL", 24200L, "BUS Ticket"
            ),
            arrayOf(
                "Dear Customer, Acct XX123 is credited with Rs 20.00 on 19-Jul-26 from NPCI BHIM. UPI:103691213332-ICICI Bank.",
                "Acct {account} is credited with Rs {amount} on {date} from {description}. UPI",
                2L, "ICICI UPI Credit TPL", 2000L, "NPCI BHIM"
            ),
            arrayOf(
                "ICICI Bank Account XX123 is credited with Rs 61,000.00 on 03-Jul-26 by Account linked to mobile number XXXXX01234. IMPS Ref. no. 618441385660.",
                "ICICI Bank Account {account} is credited with Rs {amount} on {date} by {description}. IMPS",
                2L, "ICICI IMPS Credit TPL", 6100000L, "Account linked to mobile number XXXXX01234"
            ),
            arrayOf(
                "INR 1403.36 debited DCB Bank a/c*1234 POS/Ecom txn to cafe de lar on 19-06-2026 07:59 PM.Not you?Call 0226899777 or SMS BLOCKCARD <Last 4 digits>to 9821878789",
                "INR {amount} debited DCB Bank a/c*{card} POS/Ecom txn to {description} on {date}",
                3L, "DCB POS/Ecom Debit TPL", 140336L, "cafe de lar"
            ),
            arrayOf(
                "Rs. 546.00 spent from Pluxee  Meal Card wallet, card no.xx4910 on 28-06-2026 21:38:47 at SWIGGY . Avl bal Rs.9388.14. Not you call 18002106919",
                "Rs. {amount} spent from Pluxee Meal Card wallet, card no.{card} on {date} at {description}. Avl bal",
                4L, "Pluxee Meal Spend TPL", 54600L, "SWIGGY"
            ),
            arrayOf(
                "Your Pluxee Card xx4910 has been credited with INR 546.00 on Sun Jun 28 2026 22:41:31as a reversal against a previous transaction on Jun 28,2026 21:38:47.",
                "Your Pluxee Card xx{card} has been credited with INR {amount} on {date}as a {description}.",
                4L, "Pluxee Reversal TPL", 54600L, "a reversal against a previous transaction on Jun 28,2026 21:38:47"
            ),
            arrayOf(
                "Your Pluxee Card has been successfully credited with Rs.2200 towards  Meal Wallet on Thu Sep 05 2024 17:03:06. Your current Meal Wallet balance is Rs.7142.70.",
                "credited with Rs.{amount} towards{wallet} on {description}. Your",
                4L, "Pluxee Wallet Load TPL", 220000L, "Thu Sep 05 2024 17:03:06"
            ),
            arrayOf(
                "Txn Rs.25.00\nOn HDFC Bank Card 1111\nAt Q123456789@ybl \nby UPI 620436716168\nOn 23-07\nNot You?\nCall 18002586161/SMS BLOCK CC 2468 to 7308080808",
                "Txn Rs.{amount} On HDFC Bank Card {card} At {description} by UPI {upi} On {date}",
                1L, "HDFC CC UPI Debit TPL", 2500L, "Q123456789@ybl"
            ),
            arrayOf(
                "Alert! Rs. 32 refunded by someComp on 20/JUL/2026 & adjusted against HDFC Bank Credit Card 1111 View updated balance here: bank link",
                "Alert! Rs. {amount} refunded by {description} on {date} & adjusted against HDFC Bank Credit Card {card}",
                1L, "HDFC CC Refund TPL", 3200L, "someComp"
            ),
            arrayOf(
                "Payment Successful! Rs. 66093.00 from A/c **********1233 to SOMECORP via HDFC Bank NetBanking. Not you?Call 18002586161",
                "Rs. {amount} from A/c {account} to {description} via HDFC Bank NetBanking",
                1L, "HDFC NetBanking TPL", 6609300L, "SOMECORP"
            )
```

Add 2 rows to the end of `ConfidenceScorerTest.kt`'s `data()` list:

```kotlin
            arrayOf(
                "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.",
                "Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}",
                true, true, 1.0f
            ),
            arrayOf(
                "Random text with no match",
                "Spent Rs.{amount} On HDFC Bank Card {card}",
                false, false, 0.0f
            )
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.parser.RegexParserTest" --tests "com.smsexpensetracker.core.parser.ConfidenceScorerTest"`
Expected: FAIL — the new template rows throw `PatternSyntaxException` (a template is not valid standalone regex) because `RegexParser`/`ConfidenceScorer` compile the pattern directly.

- [ ] **Step 3: Implement the dispatch**

Edit `app/src/main/java/com/smsexpensetracker/core/parser/RegexParser.kt` so `parse` dispatches templates to `TemplateCompiler`:

```kotlin
object RegexParser {
    fun parse(smsBody: String, pattern: String, bankId: Long): RegexMatch? {
        if (TemplateCompiler.isTemplate(pattern)) {
            return TemplateCompiler.extract(smsBody, pattern, bankId)
        }
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val matchResult = regex.find(smsBody) ?: return null

        val amountStr = matchResult.groupValues.getOrNull(1) ?: return null
        val description = matchResult.groupValues.getOrNull(2) ?: ""

        val amount = parsePaisa(amountStr) ?: return null

        return RegexMatch(
            amount = amount,
            description = description,
            bankId = bankId,
            rawSms = smsBody
        )
    }
}
```

Edit `app/src/main/java/com/smsexpensetracker/core/parser/ConfidenceScorer.kt` so `score` compiles templates through `TemplateCompiler`:

```kotlin
object ConfidenceScorer {
    fun score(
        smsBody: String,
        pattern: String,
        hasAmount: Boolean,
        hasDescription: Boolean
    ): ConfidenceScore {
        var score = 0.0f

        if (hasAmount) score += 0.4f
        if (hasDescription) score += 0.2f

        val compiled = if (TemplateCompiler.isTemplate(pattern)) {
            TemplateCompiler.compile(pattern)
        } else {
            Regex(pattern, RegexOption.IGNORE_CASE)
        }
        val match = compiled?.find(smsBody)
        if (match != null) {
            score += 0.3f
            val groups = match.groupValues.size - 1
            score += minOf(groups * 0.05f, 0.1f)
        }

        return ConfidenceScore(minOf(score, 1.0f))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.parser.RegexParserTest" --tests "com.smsexpensetracker.core.parser.ConfidenceScorerTest"`
Expected: PASS — all 14 new template rows and 2 new confidence rows pass; all legacy rows unchanged.

- [ ] **Step 5: Run the full gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL (282 + 16 = 298).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/parser/RegexParser.kt app/src/main/java/com/smsexpensetracker/core/parser/ConfidenceScorer.kt app/src/test/java/com/smsexpensetracker/core/parser/RegexParserTest.kt app/src/test/java/com/smsexpensetracker/core/parser/ConfidenceScorerTest.kt
git commit -m "feat(parser): dispatch template vs legacy rule parsing"
```

---

### Task 3: validatePattern template branch

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/util/BankRulesValidation.kt`
- Modify: `app/src/test/java/com/smsexpensetracker/ui/util/BankRulesValidationTest.kt`

**Interfaces:**
- Consumes (from Task 1): `TemplateCompiler.isTemplate(pattern)`, `TemplateCompiler.findPlaceholders(template)`.
- Produces: `validatePattern(pattern: String): String?` — for templates, validates braces/names/`{amount}` presence; for legacy, keeps the regex-syntax check. Used by `RuleEditorScreen` (Task 4).

- [ ] **Step 1: Write the failing tests**

Add to `BankRulesValidationTest.kt`:

```kotlin
    @Test
    fun `template pattern without amount is rejected`() {
        assertEquals(
            "Pattern must include an {amount} placeholder",
            validatePattern("Your Card {card} credited")
        )
    }

    @Test
    fun `template pattern with unbalanced braces is rejected`() {
        assertEquals(
            "Pattern has unbalanced braces",
            validatePattern("Rs.{amount} On {date")
        )
    }

    @Test
    fun `template pattern with empty placeholder is rejected`() {
        assertEquals(
            "Pattern contains an empty {} placeholder",
            validatePattern("Rs.{amount} {}")
        )
    }

    @Test
    fun `template pattern with invalid name is rejected`() {
        assertEquals(
            "Placeholder names may only contain letters and digits, and must start with a letter",
            validatePattern("Rs.{amount} {ab-cd}")
        )
    }

    @Test
    fun `template pattern with amount is allowed`() {
        assertNull(validatePattern("Rs.{amount} On {date}"))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.util.BankRulesValidationTest"`
Expected: FAIL — `validatePattern` still treats templates as regex and reports "Pattern must be a valid regular expression".

- [ ] **Step 3: Implement the template branch**

Edit `app/src/main/java/com/smsexpensetracker/ui/util/BankRulesValidation.kt`. Add the import and replace `validatePattern`:

Add `import com.smsexpensetracker.core.parser.TemplateCompiler` to the imports.

```kotlin
fun validatePattern(pattern: String): String? {
    val trimmed = pattern.trim()
    if (trimmed.isEmpty()) return "Pattern is required"
    if (TemplateCompiler.isTemplate(trimmed)) {
        if (trimmed.count { it == '{' } != trimmed.count { it == '}' }) {
            return "Pattern has unbalanced braces"
        }
        if ("{}" in trimmed) {
            return "Pattern contains an empty {} placeholder"
        }
        val names = TemplateCompiler.findPlaceholders(trimmed)
        var rest = trimmed
        names.forEach { rest = rest.replace("{$it}", "") }
        if ('{' in rest || '}' in rest) {
            return "Placeholder names may only contain letters and digits, and must start with a letter"
        }
        if (names.none { it == "amount" }) {
            return "Pattern must include an {amount} placeholder"
        }
        return null
    }
    return try {
        Pattern.compile(trimmed)
        null
    } catch (e: PatternSyntaxException) {
        "Pattern must be a valid regular expression"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.util.BankRulesValidationTest"`
Expected: PASS — 18 tests (13 existing + 5 new), 0 failures.

- [ ] **Step 5: Run the full gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL (298 + 5 = 303).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/util/BankRulesValidation.kt app/src/test/java/com/smsexpensetracker/ui/util/BankRulesValidationTest.kt
git commit -m "feat(ui): validate template rule patterns"
```

---

### Task 4: Rule editor copy + template round-trip test

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleEditorScreen.kt`
- Modify: `app/src/test/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModelTest.kt`

**Interfaces:**
- Consumes (from Task 2): `RegexParser.parse` now handles templates end-to-end, so `RuleEditorViewModel.onTest()` (unchanged) already exercises templates.
- Produces: updated supporting text + "How it works & examples" copy teaching template syntax; one ViewModel test proving a template pattern yields amount + description through the editor's Test flow.

- [ ] **Step 1: Write the failing test**

Add to `RuleEditorViewModelTest.kt`:

```kotlin
    @Test
    fun `test with template pattern extracts amount and description`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(
            "Your Pluxee Card xx4910 has been credited with INR 546.00 on Sun Jun 28 2026 22:41:31as a reversal against a previous transaction on Jun 28,2026 21:38:47."
        )
        vm.onPatternChange(
            "Your Pluxee Card xx{card} has been credited with INR {amount} on {date}as a {description}."
        )
        vm.onTest()
        val state = vm.uiState.value
        assertTrue(state.hasTested)
        assertNotNull(state.testResult)
        assertEquals(54600L, state.testResult?.amount)
        assertEquals("a reversal against a previous transaction on Jun 28,2026 21:38:47", state.testResult?.description)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.banks.RuleEditorViewModelTest"`
Expected: FAIL — this is the red step only in the sense that the test is new; if the parser dispatch (Task 2) is in place the test already passes. If it FAILS, do not proceed to the copy change until the failure is understood (expected causes: Task 2 not merged, or a real parser bug — investigate before continuing).

- [ ] **Step 3: Update the supporting text**

In `RuleEditorScreen.kt`, replace the Pattern field's supporting text (currently `Text("Group 1 = amount, Group 2 = description")` at line ~137):

```kotlin
                        Text("Use {amount} and {description}; any other {name} anchors the match")
```

- [ ] **Step 4: Update "How it works & examples"**

In `RuleEditorScreen.kt`, replace the entire `AnimatedVisibility(visible = examplesExpanded) { ... }` block content (lines ~149-174) with:

```kotlin
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "A pattern matches the SMS text. Put {amount} where the money appears " +
                            "and {description} where the merchant or remark appears. Any other " +
                            "{name} (like {card}) anchors a variable part of the message. Spaces " +
                            "are flexible — one space in the pattern matches any spacing. Test " +
                            "against a real SMS before saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "UPI debit:\nICICI Bank Acct {account} debited for Rs {amount} on {date}; {description} credited. UPI",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Card spend:\nSpent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "UPI credit:\nAcct {account} is credited with Rs {amount} on {date} from {description}. UPI",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
```

- [ ] **Step 5: Run the full gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL (303 + 1 = 304).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleEditorScreen.kt app/src/test/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModelTest.kt
git commit -m "feat(ui): document template rule syntax in rule editor"
```

---

### Task 5: Full gate + docs

**Files:**
- Modify: `TODO.md`

- [ ] **Step 1: Run the full clean gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 304 tests, 0 failures, 0 errors, `app-debug.apk` built.

- [ ] **Step 2: Update TODO.md**

Under the SMS rule management bullet (Task 14), add a sub-bullet noting the template syntax:

```
- [x] Rule patterns support {amount}/{description} template syntax (legacy regex still works)
```

- [ ] **Step 3: Commit**

```bash
git add TODO.md
git commit -m "docs: note template rule syntax in TODO"
```

---

## Post-Plan Verification (self-review)

- **Spec coverage:** §2 goals — templates (§4), flexible whitespace (TemplateCompiler `appendLiteral`), `{amount}`→paisa + `{description}` + anchors + repeat-`"; "` combine (§4.2/4.3), dual-mode dispatch (Task 2), validation (Task 3), UI copy (Task 4) all covered. §4.4 detection, §4.5 examples (HDFC card + Pluxee reversal rows in Task 2), §7 error handling (validation + null-on-malformed), §8 testing — all tasks present. No gaps.
- **Placeholders:** none — every step has exact code, exact SMS strings, and exact expected values.
- **Type consistency:** `TemplateCompiler.isTemplate/compile/extract/findPlaceholders` signatures are used identically in Tasks 2-3. `RegexMatch` unchanged. `validatePattern` return type `String?` unchanged.
- **Terminal-placeholder greedy rule** (spec §4.2) implemented in `compile` via `isTerminal`; tested by `extract terminal placeholder captures to end` (Task 1).
- **Known risk:** the 14 Task 2 template rows were hand-derived against the real SMS; if any row fails, the implementer should compare the compiled-regex behavior against the legacy row for the same SMS and adjust the template literal (not the expected amount/description) — the legacy rows are the ground truth for amount/description values. Task 4 Step 2's RED is expected to already be green (Task 2 landed); a red there means investigate, not force.
