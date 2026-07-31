# Manual Transaction Entry Screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Manual Transaction Entry screen (reachable from the Transactions FAB) that validates a form, saves a `Transaction` with `parseMethod = MANUAL`, shows a success snackbar, and stays ready for the next entry.

**Architecture:** Add a `parseMethod` column (DB v1→v2 migration) to distinguish manual from SMS-parsed transactions. A `ManualEntryViewModel` holds immutable form state + validation and inserts via the existing `TransactionRepository.insert()`. The screen is a full-screen route pushed over the Transactions tab (not a bottom-nav tab). Shared `parsePaisa` helper is extracted from `RegexParser` for reuse.

**Tech Stack:** Kotlin, Compose Material3 (1.5.0-alpha24, Expressive API already opt-in), Room + KSP, Hilt, MockK, kotlinx-coroutines-test.

## Global Constraints

- Package `com.smsexpensetracker`; min SDK 28, target 36, compile 37
- All amounts as **paisa `Long`** — never `Double`/`BigDecimal` in domain/db. `parsePaisa("100.50")` → `10050L` (rupees × 100)
- Room schema exported to `app/schemas/` (committed); bump to `2.json` on build
- Enums stored as `name` string via `Converters`
- JUnit 4 with `@RunWith(Parameterized::class)` for data-driven tests; MockK for mocks; `runTest { }` for coroutines
- Build: `./gradlew assembleDebug` — Test: `./gradlew testDebugUnitTest` (no lint/typecheck)
- No code comments unless needed; follow existing file/package structure

---
## Task 1: Extract shared `parsePaisa` helper

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/core/parser/Paisa.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/core/parser/RegexParser.kt:29-35`
- Test: `app/src/test/java/com/smsexpensetracker/core/parser/PaisaTest.kt`

**Interfaces:**
- Produces: `fun parsePaisa(input: String): Long?` in package `com.smsexpensetracker.core.parser` — used by Task 3's `ManualEntryViewModel`.

- [ ] **Step 1: Write the failing test**

Create `PaisaTest.kt`:
```kotlin
package com.smsexpensetracker.core.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PaisaTest(
    private val input: String,
    private val expected: Long?
) {
    @Test
    fun parses() {
        assertEquals(expected, parsePaisa(input))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun data() = listOf(
            arrayOf("100.50", 10050L),
            arrayOf("1,250.50", 125050L),
            arrayOf("45", 4500L),
            arrayOf("0.29", 29L),
            arrayOf("0", 0L),
            arrayOf("", null),
            arrayOf("abc", null),
            arrayOf("1.234", null),
            arrayOf("-10", null)
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.core.parser.PaisaTest"`
Expected: FAIL — "Cannot access 'parsePaisa': it is not visible" or unresolved reference.

- [ ] **Step 3: Create the shared helper**

Create `Paisa.kt`:
```kotlin
package com.smsexpensetracker.core.parser

fun parsePaisa(input: String): Long? {
    val cleaned = input.trim().replace(",", "")
    val match = Regex("^(\\d+)(?:\\.(\\d{1,2}))?$").matchEntire(cleaned) ?: return null
    val rupees = match.groupValues[1].toLongOrNull() ?: return null
    val paise = match.groupValues.getOrNull(2)?.padEnd(2, '0')?.toLongOrNull() ?: 0L
    return rupees * 100 + paise
}
```

- [ ] **Step 4: Point `RegexParser` at the shared helper**

Replace the private `parsePaisa` in `RegexParser.kt` (lines 29-35) so `RegexParser` calls the shared function. Delete the private method; `parse(smsBody, pattern, bankId)` keeps calling `parsePaisa(amountStr)` which now resolves to the top-level function in the same package.

- [ ] **Step 5: Run full parser + new tests to verify pass**

Run: `./gradlew testDebugUnitTest`
Expected: PASS — PaisaTest 9/9 AND all existing parser tests (14 SMS patterns) still pass (regex is stricter but every real pattern amount fits `^\d+(\.\d{1,2})?$`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/parser/Paisa.kt app/src/main/java/com/smsexpensetracker/core/parser/RegexParser.kt app/src/test/java/com/smsexpensetracker/core/parser/PaisaTest.kt
git commit -m "feat: extract shared parsePaisa helper with exact paisa math"
```

---
## Task 2: `parseMethod` data layer + DB migration v1→v2

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/entity/TransactionEntity.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/Converters.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/domain/model/Transaction.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/SmsExpenseDatabase.kt`
- Modify: `app/build.gradle.kts` (expose `schemas/` to androidTest assets so `MigrationTestHelper` can find the schema JSON)
- Test: `app/src/test/java/com/smsexpensetracker/data/repository/TransactionRepositoryImplTest.kt`
- Test: `app/src/androidTest/java/com/smsexpensetracker/core/database/MigrationTest.kt`
- (generated) `app/schemas/.../2.json`

**Interfaces:**
- Consumes: `TransactionRepository.insert(transaction: Transaction): Long` (unchanged signature)
- Produces: `enum ParseMethod { SMS, MANUAL }` in BOTH `core.database.entity` and `domain.model`; `TransactionEntity.parseMethod`; `Transaction.parseMethod`; `SmsExpenseDatabase.MIGRATION_1_2`. Task 3 constructs `Transaction(..., parseMethod = ParseMethod.MANUAL)` using the **domain** `ParseMethod`.

- [ ] **Step 1: Write the failing mapper test**

In `TransactionRepositoryImplTest.kt` add imports and one test proving the mapper round-trips `parseMethod` MANUAL into the entity:
```kotlin
import com.smsexpensetracker.core.database.entity.ParseMethod
import com.smsexpensetracker.domain.model.ParseMethod as DomainParseMethod

@Test
fun `insert maps manual parseMethod into entity`() = runTest {
    coEvery { transactionDao.insert(any<TransactionEntity>()) } returns 5L
    repo.insert(
        Transaction(
            id = 0L, bankId = 1L, amount = 2500L,
            transactionType = com.smsexpensetracker.domain.model.TransactionType.DEBIT,
            description = "Zomato", transactionDate = date, categoryId = null,
            rawSms = "", smsTimestamp = 0L, createdAt = date,
            parseMethod = DomainParseMethod.MANUAL
        )
    )
    coVerify {
        transactionDao.insert(
            match<TransactionEntity> {
                it.parseMethod == ParseMethod.MANUAL && it.rawSms == "" && it.smsTimestamp == 0L
            }
        )
    }
}
```

Also extend `assertTransaction` (line 177) to compare parseMethod:
```kotlin
assertEquals(expected.parseMethod.name, actual.parseMethod.name)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.repository.TransactionRepositoryImplTest"`
Expected: FAIL — compile error, `ParseMethod` / `parseMethod` unresolved.

- [ ] **Step 3: Add core enum + entity field**

In `TransactionEntity.kt`, add before the `@Entity`:
```kotlin
enum class ParseMethod { SMS, MANUAL }
```
Add to the data class (after `createdAt`):
```kotlin
val parseMethod: ParseMethod = ParseMethod.SMS
```

- [ ] **Step 4: Add type converters**

In `Converters.kt`, add:
```kotlin
@TypeConverter
fun fromParseMethod(value: ParseMethod): String = value.name

@TypeConverter
fun toParseMethod(value: String): ParseMethod = ParseMethod.valueOf(value)
```
with import `com.smsexpensetracker.core.database.entity.ParseMethod`.

- [ ] **Step 5: Add domain enum + model field**

In `domain/model/Transaction.kt`, add:
```kotlin
enum class ParseMethod { SMS, MANUAL }
```
Add to the data class (after `createdAt`):
```kotlin
val parseMethod: ParseMethod = ParseMethod.SMS
```

- [ ] **Step 6: Update mappers**

In `TransactionRepositoryImpl.kt`:
- `Transaction.toEntity()` — add named arg after `createdAt`:
  `parseMethod = com.smsexpensetracker.core.database.entity.ParseMethod.valueOf(parseMethod.name)`
- `TransactionEntity.toDomain()` — add named arg after `createdAt`:
  `parseMethod = com.smsexpensetracker.domain.model.ParseMethod.valueOf(parseMethod.name)`

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.data.repository.TransactionRepositoryImplTest"`
Expected: PASS (new test + round-trip with default SMS).

- [ ] **Step 8: Bump DB version + add migration**

In `SmsExpenseDatabase.kt`:
- Change `version = 1` → `version = 2`
- Imports: `androidx.room.migration.Migration`, `androidx.sqlite.db.SupportSQLiteDatabase`
- In `companion object` add:
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN parseMethod TEXT NOT NULL DEFAULT 'SMS'")
    }
}
```
- In `Room.databaseBuilder(...)` chain add `.addMigrations(MIGRATION_1_2)` (after `.addCallback(SeedDatabaseCallback())`).

- [ ] **Step 9: Add migration androidTest**

Create `app/src/androidTest/java/com/smsexpensetracker/core/database/MigrationTest.kt`:
```kotlin
package com.smsexpensetracker.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SmsExpenseDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesBankData_andAddsParseMethod() {
        helper.createDatabase("migration-test", 1).use { db ->
            db.execSQL("INSERT INTO banks (id, name, smsSender) VALUES (1, 'HDFC Bank', 'HDFCBK')")
            db.execSQL(
                "INSERT INTO transactions (bankId, amount, type, description, transactionDate, categoryId, rawSms, smsTimestamp, createdAt) " +
                    "VALUES (1, 1000, 'DEBIT', 'test desc', 1750000000, NULL, 'raw sms', 1750000000, 1750000000)"
            )
        }

        val db = helper.runMigrationsAndValidate("migration-test", 2, true, SmsExpenseDatabase.MIGRATION_1_2)

        db.query("SELECT name FROM banks WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("HDFC Bank", cursor.getString(0))
        }
        db.query("SELECT parseMethod FROM transactions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("SMS", cursor.getString(0))
        }
    }
}
```

- [ ] **Step 10: Expose schemas to androidTest assets**

`MigrationTestHelper` reads the exported schema JSON from the androidTest APK's assets under `schemas/`. Without this, the test compiles but fails at runtime. In `app/build.gradle.kts` add inside the `android { }` block (after `buildFeatures`):

```kotlin
    sourceSets {
        getByName("androidTest").assets.srcDirs(files("$projectDir/schemas"))
    }
```

Verify with: `./gradlew assembleDebugAndroidTest` — Expected: BUILD SUCCESSFUL (androidTest compiles; it only runs on a device/emulator via `connectedDebugAndroidTest`).

- [ ] **Step 11: Build to regenerate schema 2.json + run unit tests**

Run: `./gradlew assembleDebug` — Expected: BUILD SUCCESSFUL, `app/schemas/com.smsexpensetracker.core.database.SmsExpenseDatabase/2.json` created with `parseMethod` column.
Run: `./gradlew testDebugUnitTest` — Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add app/src app/schemas
git commit -m "feat: add parseMethod column with v1->v2 migration"
```

---
## Task 3: `ManualEntryViewModel` + tests

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryViewModelTest.kt`

**Interfaces:**
- Consumes: `parsePaisa` (Task 1), `BankRepository.getAllBanks()`, `CategoryRepository.getAllCategories()`, `TransactionRepository.insert()`, domain `ParseMethod` (Task 2)
- Produces:
  - `data class FormErrors(val amount: String? = null, val payee: String? = null)`
  - `data class ManualEntryUiState(...)` (fields below)
  - `class ManualEntryViewModel` with `val uiState: StateFlow<ManualEntryUiState>`, `onAmountChange(String)`, `onTypeChange(TransactionType)`, `onDateChange(LocalDate)`, `onBankChange(Long)`, `onPayeeChange(String)`, `onReferenceChange(String)`, `onCategoryChange(Long?)`, `save()`, `consumeSavedSnackbar()`

- [ ] **Step 1: Write the failing tests**

Create `ManualEntryViewModelTest.kt`:
```kotlin
package com.smsexpensetracker.ui.screens.manualentry

import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ManualEntryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val bankRepository = mockk<BankRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val transactionRepository = mockk<TransactionRepository>()

    private val hdfc = Bank(id = 1, name = "HDFC Bank", smsSender = "HDFCBK")
    private val icici = Bank(id = 2, name = "ICICI Bank", smsSender = "ICICIB")
    private val food = Category(id = 3, name = "Food", color = "FF0000")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { bankRepository.getAllBanks() } returns flowOf(listOf(hdfc, icici))
        every { categoryRepository.getAllCategories() } returns flowOf(listOf(food))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ManualEntryViewModel =
        ManualEntryViewModel(bankRepository, categoryRepository, transactionRepository)

    @Test
    fun `init defaults bank to first bank`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(1L, vm.uiState.value.bankId)
        assertEquals(listOf(hdfc, icici), vm.uiState.value.banks)
    }

    @Test
    fun `blank amount shows error and does not insert`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onPayeeChange("Zomato")
        vm.save()
        advanceUntilIdle()
        assertEquals("Amount is required", vm.uiState.value.errors.amount)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `invalid amount shows error and does not insert`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("abc")
        vm.onPayeeChange("Zomato")
        vm.save()
        advanceUntilIdle()
        assertEquals("Enter a valid amount", vm.uiState.value.errors.amount)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `zero amount shows error and does not insert`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("0")
        vm.onPayeeChange("Zomato")
        vm.save()
        advanceUntilIdle()
        assertEquals("Amount must be greater than zero", vm.uiState.value.errors.amount)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `blank payee shows error and does not insert`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100.50")
        vm.save()
        advanceUntilIdle()
        assertEquals("Payee is required", vm.uiState.value.errors.payee)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `overlong payee shows error and does not insert`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100.50")
        vm.onPayeeChange("x".repeat(201))
        vm.save()
        advanceUntilIdle()
        assertEquals("Payee must be 200 characters or fewer", vm.uiState.value.errors.payee)
        coVerify(exactly = 0) { transactionRepository.insert(any()) }
    }

    @Test
    fun `valid save inserts transaction in paisa with manual parseMethod`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("1,250.50")
        vm.onPayeeChange("Zomato")
        vm.onReferenceChange("ORD-123")
        vm.onTypeChange(TransactionType.CREDIT)
        vm.onCategoryChange(3)
        vm.save()
        advanceUntilIdle()

        coVerify {
            transactionRepository.insert(
                match<Transaction> {
                    it.amount == 125050L &&
                        it.transactionType == TransactionType.CREDIT &&
                        it.description == "Zomato · ORD-123" &&
                        it.categoryId == 3L &&
                        it.parseMethod == ParseMethod.MANUAL &&
                        it.rawSms == "" &&
                        it.smsTimestamp == 0L
                }
            )
        }
    }

    @Test
    fun `save without reference uses payee as description`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("45")
        vm.onPayeeChange("Zomato")
        vm.save()
        advanceUntilIdle()
        coVerify {
            transactionRepository.insert(match<Transaction> { it.description == "Zomato" && it.amount == 4500L })
        }
    }

    @Test
    fun `save uses chosen date at start of day`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100")
        vm.onPayeeChange("Zomato")
        vm.onDateChange(LocalDate.of(2026, 7, 15))
        vm.save()
        advanceUntilIdle()
        coVerify {
            transactionRepository.insert(match<Transaction> { it.transactionDate.toLocalDate() == LocalDate.of(2026, 7, 15) })
        }
    }

    @Test
    fun `after save form clears and snackbar flag set`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100.50")
        vm.onPayeeChange("Zomato")
        vm.onReferenceChange("R1")
        vm.onCategoryChange(3)
        vm.save()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("", state.amountInput)
        assertEquals("", state.payee)
        assertEquals("", state.reference)
        assertNull(state.categoryId)
        assertTrue(state.showSavedSnackbar)
        assertEquals(TransactionType.DEBIT, state.type)
        assertEquals(1L, state.bankId)
    }

    @Test
    fun `consumeSavedSnackbar resets flag`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onAmountChange("100")
        vm.onPayeeChange("Zomato")
        vm.save()
        advanceUntilIdle()
        vm.consumeSavedSnackbar()
        assertEquals(false, vm.uiState.value.showSavedSnackbar)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.manualentry.ManualEntryViewModelTest"`
Expected: FAIL — `ManualEntryViewModel` unresolved.

- [ ] **Step 3: Implement the ViewModel**

Create `ManualEntryViewModel.kt`:
```kotlin
package com.smsexpensetracker.ui.screens.manualentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.parser.parsePaisa
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class FormErrors(
    val amount: String? = null,
    val payee: String? = null
)

data class ManualEntryUiState(
    val amountInput: String = "",
    val type: TransactionType = TransactionType.DEBIT,
    val transactionDate: LocalDate = LocalDate.now(),
    val bankId: Long? = null,
    val payee: String = "",
    val reference: String = "",
    val categoryId: Long? = null,
    val banks: List<Bank> = emptyList(),
    val categories: List<Category> = emptyList(),
    val errors: FormErrors = FormErrors(),
    val isSaving: Boolean = false,
    val showSavedSnackbar: Boolean = false
)

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val bankRepository: BankRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualEntryUiState())
    val uiState: StateFlow<ManualEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val banks = bankRepository.getAllBanks().first()
            val categories = categoryRepository.getAllCategories().first()
            _uiState.update {
                it.copy(
                    banks = banks,
                    categories = categories,
                    bankId = it.bankId ?: banks.firstOrNull()?.id
                )
            }
        }
    }

    fun onAmountChange(value: String) = _uiState.update {
        it.copy(
            amountInput = value.filter { c -> c.isDigit() || c == '.' || c == ',' },
            errors = it.errors.copy(amount = null)
        )
    }

    fun onTypeChange(type: TransactionType) = _uiState.update { it.copy(type = type) }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(transactionDate = date) }

    fun onBankChange(id: Long) = _uiState.update { it.copy(bankId = id) }

    fun onPayeeChange(value: String) = _uiState.update {
        it.copy(payee = value, errors = it.errors.copy(payee = null))
    }

    fun onReferenceChange(value: String) = _uiState.update { it.copy(reference = value) }

    fun onCategoryChange(id: Long?) = _uiState.update { it.copy(categoryId = id) }

    fun save() {
        val current = _uiState.value
        if (current.isSaving) return

        val amountPaisa = parsePaisa(current.amountInput)
        val errors = FormErrors(
            amount = when {
                current.amountInput.isBlank() -> "Amount is required"
                amountPaisa == null -> "Enter a valid amount"
                amountPaisa <= 0 -> "Amount must be greater than zero"
                else -> null
            },
            payee = when {
                current.payee.isBlank() -> "Payee is required"
                current.payee.length > 200 -> "Payee must be 200 characters or fewer"
                else -> null
            }
        )

        if (errors.amount != null || errors.payee != null) {
            _uiState.update { it.copy(errors = errors) }
            return
        }

        val bankId = current.bankId ?: return
        val paisa = amountPaisa ?: return
        val description = if (current.reference.isBlank()) {
            current.payee.trim()
        } else {
            "${current.payee.trim()} · ${current.reference.trim()}"
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            transactionRepository.insert(
                Transaction(
                    id = 0L,
                    bankId = bankId,
                    amount = paisa,
                    transactionType = current.type,
                    description = description,
                    transactionDate = current.transactionDate.atStartOfDay(),
                    categoryId = current.categoryId,
                    rawSms = "",
                    smsTimestamp = 0L,
                    createdAt = LocalDateTime.now(),
                    parseMethod = ParseMethod.MANUAL
                )
            )
            _uiState.update {
                it.copy(
                    amountInput = "",
                    payee = "",
                    reference = "",
                    categoryId = null,
                    errors = FormErrors(),
                    isSaving = false,
                    showSavedSnackbar = true
                )
            }
        }
    }

    fun consumeSavedSnackbar() = _uiState.update { it.copy(showSavedSnackbar = false) }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.manualentry.ManualEntryViewModelTest"`
Expected: PASS — all 12 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryViewModelTest.kt
git commit -m "feat: add ManualEntryViewModel with validation and save flow"
```

---
## Task 4: `ManualEntryScreen` + navigation wiring

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryScreen.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `ManualEntryViewModel` + `ManualEntryUiState` (Task 3)
- Produces: `fun ManualEntryScreen(onBack: () -> Unit, viewModel: ManualEntryViewModel = hiltViewModel())`; nav route string `"manual_entry"`

- [ ] **Step 1: Implement the screen**

Create `ManualEntryScreen.kt`:
```kotlin
package com.smsexpensetracker.ui.screens.manualentry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.smsexpensetracker.domain.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    onBack: () -> Unit,
    viewModel: ManualEntryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var bankExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = state.transactionDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    )

    LaunchedEffect(state.showSavedSnackbar) {
        if (state.showSavedSnackbar) {
            snackbarHostState.showSnackbar("Transaction saved")
            viewModel.consumeSavedSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.amountInput,
                onValueChange = viewModel::onAmountChange,
                label = { Text("Amount") },
                leadingIcon = { Text("₹") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = state.errors.amount != null,
                supportingText = state.errors.amount?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TransactionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TransactionType.entries.size
                        )
                    ) {
                        Text(type.name)
                    }
                }
            }

            OutlinedTextField(
                value = state.transactionDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            )

            ExposedDropdownMenuBox(
                expanded = bankExpanded,
                onExpandedChange = { bankExpanded = it }
            ) {
                OutlinedTextField(
                    value = state.banks.find { it.id == state.bankId }?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = bankExpanded,
                    onDismissRequest = { bankExpanded = false }
                ) {
                    state.banks.forEach { bank ->
                        DropdownMenuItem(
                            text = { Text(bank.name) },
                            onClick = { viewModel.onBankChange(bank.id); bankExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.payee,
                onValueChange = viewModel::onPayeeChange,
                label = { Text("Payee") },
                singleLine = true,
                isError = state.errors.payee != null,
                supportingText = state.errors.payee?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.reference,
                onValueChange = viewModel::onReferenceChange,
                label = { Text("Reference (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = state.categories.find { it.id == state.categoryId }?.name ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = { viewModel.onCategoryChange(null); categoryExpanded = false }
                    )
                    state.categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = { viewModel.onCategoryChange(cat.id); categoryExpanded = false }
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Save")
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.onDateChange(
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
```

- [ ] **Step 2: Wire navigation**

Modify `NavGraph.kt`:
```kotlin
import com.smsexpensetracker.ui.screens.manualentry.ManualEntryScreen
```
Replace the Transactions composable:
```kotlin
composable(BottomNavItem.Transactions.route) {
    TransactionsScreen(
        onNavigateToManualEntry = { navController.navigate("manual_entry") }
    )
}
```
Add after it:
```kotlin
composable("manual_entry") {
    ManualEntryScreen(onBack = { navController.popBackStack() })
}
```

- [ ] **Step 3: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (Fix any API mismatch — e.g. `SegmentedButton`/`DatePickerDialog` signatures in material3 1.5.0-alpha.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/manualentry/ManualEntryScreen.kt app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt
git commit -m "feat: add ManualEntryScreen with form and navigation route"
```

---
## Task 5: Full verification + smoke test

**Files:**
- None new (verification only)

- [ ] **Step 1: Run full unit test suite**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all existing + new tests pass (94 + PaisaTest 9 + ManualEntryViewModel 12).

- [ ] **Step 2: Build debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Emulator smoke test (manual)**

Install: `./gradlew installDebug`
Verify on device:
1. Open Transactions tab → tap `+` FAB → Manual Entry screen opens with back arrow
2. Tap "Save" empty → "Amount is required" + "Payee is required" errors appear
3. Enter amount "1,250.50", payee "Zomato", reference "ORD-123", select category Food, date = today, type = Debit (default)
4. Tap Save → snackbar "Transaction saved", form clears, stays on screen
5. Navigate back → Transactions list shows the new transaction with description "Zomato · ORD-123"
6. (Pre-existing installs) app opens without "Room cannot verify the data integrity" crash → migration ran
7. Tap an existing SMS-parsed transaction → opens detail sheet (regression: parseMethod SMS path unaffected)

- [ ] **Step 4: Commit any smoke-test fixes**

```bash
git add -A
git commit -m "fix: address issues found in Manual Entry smoke test"
```
