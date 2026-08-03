# Template-Based SMS Parsing Rules — Design Spec

**Date:** 2026-08-03
**Status:** Approved
**Task reference:** SMS rule management (bank/rules feature), rule-pattern authoring

## 1. Overview

Today, SMS parsing rules are raw regex strings. The contract is positional: **group 1 = amount, group 2 = description** (`RegexParser.kt`). Users must escape literals (`\\.`, `\\d`, `\\w`) and keep the group order right — hard for non-technical users and error-prone.

This change introduces a **template syntax**: a pattern is literal text with `{fieldName}` placeholders, e.g.

```
Your Pluxee Card xx{card} has been credited with INR {amount} on {date}as a {description}.
```

Templates are stored **verbatim** in `sms_rules.pattern` (no schema change) and are the user-facing syntax shown in the UI. A compiler turns a template into the same `Regex` machinery the parser already uses, so nothing downstream changes.

Existing regex rules keep working unchanged (**dual-mode**): a pattern containing a `{letter...}` placeholder is treated as a template; otherwise the current regex path runs.

## 2. Goals

- Users author rules with readable `{fieldName}` templates instead of positional regex groups.
- No regex escaping needed for ordinary literal text (`.`, `+`, `(` match literally).
- Flexible whitespace: a run of spaces/newlines in the template matches one-or-more whitespace in the SMS.
- `{amount}` maps to the transaction amount (paisa `Long`); `{description}` maps to the description.
- Repeated `{description}` occurrences combine their captured values into one description joined with `"; "`.
- Other placeholders (`{card}`, `{date}`, …) act as **anchors** — matched but not persisted.
- Existing regex rules parse identically; no DB migration.
- Templates appear in the UI (rule editor, bank detail list, Parser Test screen); compiled regex is never shown unless a legacy rule forces it.

## 3. Non-Goals

- Auto-converting existing regex rules to templates (unreliable; out of scope).
- Persisting anchor fields (`card`, `transactionDate`, …) to the transaction model (no schema change).
- A template-to-regex visualizer or a live debugger UI.
- Rewriting the 6 seeded rules as templates (fresh-install only; optional follow-up).
- Parser Test screen redesign (it consumes patterns unchanged).

## 4. Template Syntax

### 4.1 Grammar

```
pattern     := literal? placeholder (literal placeholder)* literal?
placeholder := '{' fieldName '}'
fieldName   := [a-zA-Z][a-zA-Z0-9]*
```

### 4.2 Compilation to Regex

`TemplateCompiler.compile(template): Regex?`:

1. **Split** the template on placeholders (regex `\{[a-zA-Z][a-zA-Z0-9]*\}`).
2. **Literal segments:**
   - Regex-escape every character.
   - Collapse interior runs of whitespace (space, tab, CR, LF) to `\s+`.
   - Drop leading and trailing whitespace (they are meaningless anchors).
3. **Placeholder → named capture group** (compiled with `RegexOption.IGNORE_CASE`, matched via `.find`):
   - `{amount}` → `(?<amount>[\d,]+(?:\.\d{1,2})?)`; first occurrence wins if repeated.
   - `{description}` (first) → `(?<description>.+?)`; second occurrence → `(?<description2>.+?)`, third → `(?<description3>.+?)`, etc.
   - Any other `{name}` → `(?<a{n}>.+?)` anchor, matched but discarded.
   - **Terminal placeholder:** if the template ends with a placeholder (no trailing literal), compile it **greedy** (`.+`) instead of non-greedy, so it captures to the end of the match rather than a single character.
4. Return `null` if the template is malformed (unbalanced braces, empty `{}`, invalid name) or no `{amount}` is present.

### 4.3 Match semantics

- Amount: `parsePaisa(match.group["amount"])` — null → no match (returns null), mirroring today's contract.
- Description: join all `{description}` captures with `"; "`, each trimmed.
- Anchors: captured, ignored.
- Terminal placeholders capture to the end of the match (see §4.2).
- Result is the existing `RegexMatch(amount, description, bankId, rawSms)` shape.

### 4.4 Mode detection

`isTemplate(pattern)` = true iff `pattern` contains a `{letter...}` placeholder. `\d{4}` is NOT a template (`{4}` doesn't start with a letter). Legacy regex patterns never contain `{letter...}`, so detection is unambiguous in practice.

### 4.5 Examples

SMS: `Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161`

| Pattern | Amount (paisa) | Description |
|---|---|---|
| `Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}` | `483176` | `Acme Inc.` |

SMS: `Your Pluxee Card xx4910 has been credited with INR 546.00 on Sun Jun 28 2026 22:41:31as a reversal against a previous transaction on Jun 28,2026 21:38:47.`

| Pattern | Amount (paisa) | Description |
|---|---|---|
| `Your Pluxee Card xx{card} has been credited with INR {amount} on {date}as a {description}.` | `54600` | `a reversal against a previous transaction on Jun 28,2026 21:38:47` |

Repeated description: pattern `{amount} debited at {description} ref {description}` against `100.00 debited at Swiggy ref 1234` → amount `10000`, description `Swiggy; 1234` (the terminal `{description}` is greedy and captures to end).

## 5. Architecture

```
core/parser/
  TemplateCompiler.kt   (new)  — isTemplate(), compile(), extract()
  RegexParser.kt        (edit) — dispatch template vs legacy
  ConfidenceScorer.kt   (edit) — compile patterns through the shared compiler
ui/util/BankRulesValidation.kt (edit) — template validation branch
ui/screens/banks/RuleEditorScreen.kt (edit) — copy + examples
```

- `RegexParser.parse(smsBody, pattern, bankId)`: if `isTemplate(pattern)` → `TemplateCompiler.compile` + `extract`; else existing group-1/group-2 path. Same `RegexMatch` out.
- `ConfidenceScorer.score`: compile the pattern through the same compiler and count effective groups. (Without this, a template string would crash `Regex("{amount}")` — `PatternSyntaxException`.)
- `validatePattern(pattern)`: template branch — balanced braces, valid names, no empty `{}`, requires `{amount}`; legacy branch — unchanged `Pattern.compile` check.
- Rule editor: supporting text and "How it works & examples" rewritten for templates.

## 6. Data

No schema change. `sms_rules.pattern` stores the template string verbatim. Legacy regex values remain valid; they take the legacy path.

## 7. Error handling

- Malformed templates are rejected at **validation time** (rule editor) — the user gets an inline error and cannot save.
- `TemplateCompiler.compile` returns null on malformed input; at parse time this yields no match (defensive; validated rules should never reach it).
- Legacy regex compile errors continue to behave as today (validate-before-save; `.find` never crashes on validated rules).

## 8. Testing

- `TemplateCompilerTest`: detection (`{amount}` template, `\d{4}` legacy), compile happy path, flexible whitespace, repeated `{description}` combine, terminal `{description}` greedy capture to end, anchor discard, first-`{amount}`-wins, malformed templates → null.
- `RegexParserTest` additions (parameterized): real SMS patterns rewritten as templates — HDFC card/UPI/NEFT, ICICI debit/credit/IMPS, Pluxee reversal/meal-wallet — asserting exact paisa + description.
- `ConfidenceScorerTest` addition: a template pattern compiles, doesn't throw, and scores.
- `BankRulesValidationTest` additions: missing `{amount}`, unbalanced braces, bad name, valid template, legacy still OK.
- `RuleEditorViewModelTest` addition: a template-pattern round-trip yields amount + description.
- All existing tests remain green (legacy path unchanged). Build gate: `./gradlew testDebugUnitTest assembleDebug`.

## 9. File list

- New: `app/src/main/java/com/smsexpensetracker/core/parser/TemplateCompiler.kt`
- New: `app/src/test/java/com/smsexpensetracker/core/parser/TemplateCompilerTest.kt`
- Edit: `app/src/main/java/com/smsexpensetracker/core/parser/RegexParser.kt`
- Edit: `app/src/main/java/com/smsexpensetracker/core/parser/ConfidenceScorer.kt`
- Edit: `app/src/main/java/com/smsexpensetracker/ui/util/BankRulesValidation.kt`
- Edit: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleEditorScreen.kt`
- Edit: `app/src/test/java/com/smsexpensetracker/core/parser/RegexParserTest.kt`
- Edit: `app/src/test/java/com/smsexpensetracker/core/parser/ConfidenceScorerTest.kt`
- Edit: `app/src/test/java/com/smsexpensetracker/ui/util/BankRulesValidationTest.kt`
- Edit: `app/src/test/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModelTest.kt`
