# Bank & SMS Rule Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bank CRUD (with guarded deletion) and per-bank SMS rule CRUD with enable/disable toggles behind a Settings → Banks & Rules drill-down, with disabled rules excluded from the parser.

**Architecture:** Follows the established MVVM + drill-down pattern from Categories: extend `BankRepository`/`SmsRuleRepository` with write methods, add `isActive` to `SmsRuleEntity` via schema v5, filter inactive rules out of `SmsSyncUseCase` and `ParserViewModel`, and add `ui/screens/banks/` screens + ViewModels wired through new navigation routes.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Hilt, Room, MockK, kotlinx-coroutines-test.

## Global Constraints

- All amounts are paisa `Long` — do not introduce `Double`/`BigDecimal`.
- Do NOT add comments to code unless asked.
- Follow existing patterns: `@HiltViewModel`, `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`, `StandardTestDispatcher` + `Dispatchers.setMain` in tests, `coVerify(exactly = 0)` for zero-call assertions on suspend functions.
- No Robolectric; unit tests via MockK + `runTest`. MigrationTest is a device test (not run on host).
- Gate after every task: `./gradlew testDebugUnitTest assembleDebug`. If KSP/Hilt flakes, `./gradlew clean` first.
- `SmsRule` domain model gains `isActive: Boolean = true` (default keeps all existing constructions compiling).
- Spec: `docs/superpowers/specs/2026-08-01-bank-rule-management-design.md`.

---

### Task 1: Schema v5 — isActive column + migration

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/entity/SmsRuleEntity.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/SmsExpenseDatabase.kt`
- Modify: `app/src/androidTest/java/com/smsexpensetracker/core/database/MigrationTest.kt`
- Add (generated): `app/schemas/com.smsexpensetracker.core.database.SmsExpenseDatabase/5.json`

**Interfaces:**
- Produces: `SmsRuleEntity(id, bankId, pattern, description, isActive: Boolean = true)`; `MIGRATION_4_5`; DB version 5. Later tasks consume the entity field via the domain model + repository mappers.

- [ ] **Step 1: Add isActive to the entity**

Open `app/src/main/java/com/smsexpensetracker/core/database/entity/SmsRuleEntity.kt` and add the field:

```kotlin
data class SmsRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bankId: Long,
    val pattern: String,
    val description: String,
    val isActive: Boolean = true
)
```

- [ ] **Step 2: Add MIGRATION_4_5 and bump version**

Open `app/src/main/java/com/smsexpensetracker/core/database/SmsExpenseDatabase.kt`:
- Change `version = 4,` to `version = 5,`
- Add after `MIGRATION_2_3`:

```kotlin
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `categories` SET `isDefault` = 1 WHERE `id` BETWEEN 1 AND 14")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sms_rules` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1")
            }
        }
```

Wait — `MIGRATION_3_4` already exists from the Categories feature. Check the file: if `MIGRATION_3_4` is present, only add `MIGRATION_4_5` and change `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)` to include `MIGRATION_4_5`. Do not duplicate `MIGRATION_3_4`.

- [ ] **Step 3: Add migration test**

Open `app/src/androidTest/java/com/smsexpensetracker/core/database/MigrationTest.kt` and add:

```kotlin
    @Test
    fun migrate4To5_addsIsActiveColumn() {
        helper.createDatabase("migration-test-v5", 4).use { db ->
            db.execSQL("INSERT INTO banks (id, name, smsSender) VALUES (1, 'HDFC Bank', 'HDFCBK')")
            db.execSQL(
                "INSERT INTO sms_rules (id, bankId, pattern, description) " +
                    "VALUES (1, 1, 'Spent Rs\\\\.([\\\\d,.]+) On HDFC Bank Card', 'HDFC CC Debit')"
            )
        }

        val db = helper.runMigrationsAndValidate("migration-test-v5", 5, true, SmsExpenseDatabase.MIGRATION_4_5)

        db.query("SELECT isActive FROM sms_rules WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }
        db.close()
    }
```

- [ ] **Step 4: Build + commit**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL; `app/schemas/com.smsexpensetracker.core.database.SmsExpenseDatabase/5.json` is generated.

```bash
git add app/src/main/java/com/smsexpensetracker/core/database/entity/SmsRuleEntity.kt app/src/main/java/com/smsexpensetracker/core/database/SmsExpenseDatabase.kt app/src/androidTest/java/com/smsexpensetracker/core/database/MigrationTest.kt app/schemas/
git commit -m "feat: add isActive column to sms_rules (schema v5)"
```

---

### Task 2: Domain model + repository write methods

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/domain/model/SmsRule.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/domain/repository/BankRepository.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/domain/repository/SmsRuleRepository.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/BankRepositoryImpl.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/SmsRuleRepositoryImpl.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt`

**Interfaces:**
- Consumes: `SmsRuleEntity.isActive` (Task 1); existing `BankDao`/`SmsRuleDao`/`TransactionDao`.
- Produces:
  - `SmsRule(id: Long, bankId: Long, pattern: String, description: String, isActive: Boolean = true)`
  - `BankRepository`: `insert(bank: Bank): Long`, `update(bank: Bank)`, `delete(bank: Bank)`, `countTransactions(bankId: Long): Int`
  - `SmsRuleRepository`: `update(rule: SmsRule)`, `delete(rule: SmsRule)` (insert already exists, mapper now carries isActive)
  - `TransactionDao.countByBank(bankId: Long): Int`
  Later tasks consume these via ViewModels.

- [ ] **Step 1: Add isActive to domain model**

Open `app/src/main/java/com/smsexpensetracker/domain/model/SmsRule.kt`:

```kotlin
data class SmsRule(
    val id: Long,
    val bankId: Long,
    val pattern: String,
    val description: String,
    val isActive: Boolean = true
)
```

- [ ] **Step 2: Add BankRepository methods**

Open `app/src/main/java/com/smsexpensetracker/domain/repository/BankRepository.kt`, add:

```kotlin
    suspend fun insert(bank: Bank): Long

    suspend fun update(bank: Bank)

    suspend fun delete(bank: Bank)

    suspend fun countTransactions(bankId: Long): Int
```

- [ ] **Step 3: Add SmsRuleRepository methods**

Open `app/src/main/java/com/smsexpensetracker/domain/repository/SmsRuleRepository.kt`, add:

```kotlin
    suspend fun update(rule: SmsRule)

    suspend fun delete(rule: SmsRule)
```

- [ ] **Step 4: Add count query to TransactionDao**

Open `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt`, add:

```kotlin
    @Query("SELECT COUNT(*) FROM transactions WHERE bankId = :bankId")
    fun countByBank(bankId: Long): Int
```

- [ ] **Step 5: Implement in BankRepositoryImpl**

Open `app/src/main/java/com/smsexpensetracker/data/repository/BankRepositoryImpl.kt`. Add imports for `BankEntity` and the new methods:

```kotlin
    override suspend fun insert(bank: Bank): Long =
        bankDao.insert(bank.toEntity())

    override suspend fun update(bank: Bank) {
        bankDao.update(bank.toEntity())
    }

    override suspend fun delete(bank: Bank) {
        bankDao.delete(bank.toEntity())
    }

    override suspend fun countTransactions(bankId: Long): Int =
        bankDao.getTransactionCount(bankId)

    private fun Bank.toEntity() = BankEntity(id = id, name = name, smsSender = smsSender)
```

Note: `TransactionDao.countByBank` lives in `TransactionDao` — `BankRepositoryImpl` has a `BankDao`, not a `TransactionDao`. Choose ONE of these two implementations and make it consistent:
- **Option A (recommended):** add the count query to `BankDao` as `getTransactionCount(bankId)` (a `@Query` can run against any table), and `BankRepositoryImpl` calls `bankDao.getTransactionCount(bankId)`. Skip the `TransactionDao` change from Step 4.
- **Option B:** inject `TransactionDao` into `BankRepositoryImpl` and call `transactionDao.countByBank(bankId)`.

Do NOT do both. The reviewer will check for a single consistent wiring. (The plan's Step 4 assumes Option B; if you take Option A, revert Step 4's DAO change and put `getTransactionCount` in `BankDao` instead.)

- [ ] **Step 6: Implement in SmsRuleRepositoryImpl**

Open `app/src/main/java/com/smsexpensetracker/data/repository/SmsRuleRepositoryImpl.kt`, update the mapper + add methods:

```kotlin
    override suspend fun update(rule: SmsRule) {
        smsRuleDao.update(rule.toEntity())
    }

    override suspend fun delete(rule: SmsRule) {
        smsRuleDao.delete(rule.toEntity())
    }

    private fun SmsRuleEntity.toDomain() = SmsRule(id, bankId, pattern, description, isActive)

    private fun SmsRule.toEntity() = SmsRuleEntity(id, bankId, pattern, description, isActive)
```

- [ ] **Step 7: Build + commit**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL (existing 176 tests pass — `SmsRule` constructions in tests use the default `isActive`).

```bash
git add app/src/main/java/com/smsexpensetracker/domain/model/SmsRule.kt app/src/main/java/com/smsexpensetracker/domain/repository/BankRepository.kt app/src/main/java/com/smsexpensetracker/domain/repository/SmsRuleRepository.kt app/src/main/java/com/smsexpensetracker/data/repository/BankRepositoryImpl.kt app/src/main/java/com/smsexpensetracker/data/repository/SmsRuleRepositoryImpl.kt app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt app/src/main/java/com/smsexpensetracker/core/database/dao/BankDao.kt
git commit -m "feat: add bank and rule write methods to repositories"
```

---

### Task 3: Parser active-only filtering

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserViewModel.kt`
- Modify: `app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt`

**Interfaces:**
- Consumes: `SmsRule.isActive` (Task 2).
- Produces: active-only rule lists feeding `ParserEngine.parse(...)`. The new test in Step 1 is the behavioral gate.

- [ ] **Step 1: Write the failing test**

Open `app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt`, add a test after `sync parses HDFC sms and inserts a transaction`:

```kotlin
    @Test
    fun `sync ignores inactive rules`() = runTest {
        val inactiveHdfc = hdfcRule.copy(id = 99L, isActive = false)
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(inactiveHdfc))
        coEvery { transactionRepository.insertBatch(any()) } returns 0
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertEquals(SyncResult(scanned = 1, inserted = 0, unparsed = 1), result)
        coVerify(exactly = 0) { transactionRepository.insertBatch(any()) }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.SmsSyncUseCaseTest.sync ignores inactive rules"`
Expected: FAIL — the inactive rule is currently used, so the SMS parses and inserts instead of failing.

- [ ] **Step 3: Filter inactive rules in SmsSyncUseCase**

Open `app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt`, line ~49:

```kotlin
                val rules = smsRuleRepository.getAllRules().first()
```
becomes:
```kotlin
                val rules = smsRuleRepository.getAllRules().first().filter { it.isActive }
```

- [ ] **Step 4: Filter in ParserViewModel**

Open `app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserViewModel.kt`, line ~86:

```kotlin
        val rules = if (bankId != null) allRules.filter { it.bankId == bankId } else emptyList()
```
becomes:
```kotlin
        val rules = if (bankId != null) allRules.filter { it.bankId == bankId && it.isActive } else emptyList()
```

- [ ] **Step 5: Run test to verify it passes + gate**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.domain.usecase.SmsSyncUseCaseTest.sync ignores inactive rules"`
Expected: PASS.

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL (177 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCase.kt app/src/main/java/com/smsexpensetracker/ui/screens/parser/ParserViewModel.kt app/src/test/java/com/smsexpensetracker/domain/usecase/SmsSyncUseCaseTest.kt
git commit -m "feat: exclude inactive SMS rules from parsing"
```

---

### Task 4: Validation helpers

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/util/BankRulesValidation.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/util/BankRulesValidationTest.kt`

**Interfaces:**
- Consumes: `Bank` domain model.
- Produces: `validateBankName(name, existing: List<Bank>, editingId: Long?): String?`, `validateBankSender(sender): String?`, `validateRuleDescription(description): String?`, `validatePattern(pattern): String?`. The dialogs in Tasks 7-8 consume these.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/ui/util/BankRulesValidationTest.kt`:

```kotlin
package com.smsexpensetracker.ui.util

import com.smsexpensetracker.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankRulesValidationTest {

    private val existing = listOf(
        Bank(id = 1, name = "HDFC Bank", smsSender = "HDFCBK"),
        Bank(id = 2, name = "ICICI Bank", smsSender = "ICICIB")
    )

    @Test
    fun `bank name blank is rejected`() {
        assertEquals("Name is required", validateBankName("  ", existing, null))
    }

    @Test
    fun `bank name too long is rejected`() {
        assertEquals(
            "Name must be 30 characters or fewer",
            validateBankName("x".repeat(31), existing, null)
        )
    }

    @Test
    fun `bank name duplicate is rejected case-insensitively`() {
        assertEquals(
            "A bank with this name already exists",
            validateBankName("hdfc bank", existing, null)
        )
    }

    @Test
    fun `bank name same as self when editing is allowed`() {
        assertNull(validateBankName("hdfc bank", existing, 1))
    }

    @Test
    fun `bank name unique is allowed`() {
        assertNull(validateBankName("Axis Bank", existing, null))
    }

    @Test
    fun `bank sender blank is rejected`() {
        assertEquals("Sender is required", validateBankSender("   "))
    }

    @Test
    fun `bank sender non-blank is allowed`() {
        assertNull(validateBankSender("AXISB"))
    }

    @Test
    fun `rule description blank is rejected`() {
        assertEquals("Description is required", validateRuleDescription(""))
    }

    @Test
    fun `rule description too long is rejected`() {
        assertEquals(
            "Description must be 60 characters or fewer",
            validateRuleDescription("x".repeat(61))
        )
    }

    @Test
    fun `rule description valid is allowed`() {
        assertNull(validateRuleDescription("HDFC UPI Credit"))
    }

    @Test
    fun `pattern blank is rejected`() {
        assertEquals("Pattern is required", validatePattern("  "))
    }

    @Test
    fun `pattern invalid regex is rejected`() {
        assertEquals(
            "Pattern must be a valid regular expression",
            validatePattern("Spent Rs\\.([\\d,.]+")
        )
    }

    @Test
    fun `pattern valid regex is allowed`() {
        assertNull(validatePattern("Spent Rs\\.([\\d,.]+) On HDFC Bank Card"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.util.BankRulesValidationTest"`
Expected: FAIL — functions unresolved.

- [ ] **Step 3: Write the helper**

Create `app/src/main/java/com/smsexpensetracker/ui/util/BankRulesValidation.kt`:

```kotlin
package com.smsexpensetracker.ui.util

import com.smsexpensetracker.domain.model.Bank
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

fun validateBankName(name: String, existing: List<Bank>, editingId: Long?): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "Name is required"
    if (trimmed.length > 30) return "Name must be 30 characters or fewer"
    val duplicate = existing.any { it.id != editingId && it.name.equals(trimmed, ignoreCase = true) }
    if (duplicate) return "A bank with this name already exists"
    return null
}

fun validateBankSender(sender: String): String? {
    if (sender.trim().isEmpty()) return "Sender is required"
    return null
}

fun validateRuleDescription(description: String): String? {
    val trimmed = description.trim()
    if (trimmed.isEmpty()) return "Description is required"
    if (trimmed.length > 60) return "Description must be 60 characters or fewer"
    return null
}

fun validatePattern(pattern: String): String? {
    val trimmed = pattern.trim()
    if (trimmed.isEmpty()) return "Pattern is required"
    return try {
        Pattern.compile(trimmed)
        null
    } catch (e: PatternSyntaxException) {
        "Pattern must be a valid regular expression"
    }
}
```

Note: `Pattern.compile` catches the syntax error at compile time (safer than Kotlin's `Regex()` which can throw later). The test uses a deliberately unbalanced-paren pattern (`Spent Rs\\.([\\d,.]+`) that must fail `Pattern.compile`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.util.BankRulesValidationTest"`
Expected: PASS (13 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/util/BankRulesValidation.kt app/src/test/java/com/smsexpensetracker/ui/util/BankRulesValidationTest.kt
git commit -m "feat: add bank and rule validation helpers"
```

---

### Task 5: BankManagementViewModel

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankManagementViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/banks/BankManagementViewModelTest.kt`

**Interfaces:**
- Consumes: `BankRepository` (read + write from Task 2): `getAllBanks()`, `insert`, `update`, `delete`, `countTransactions`.
- Produces: `BankManagementViewModel` with `val banks: StateFlow<List<Bank>>`, `val transactionCounts: StateFlow<Map<Long, Int>>`, `fun addBank(name: String, smsSender: String)`, `fun updateBank(bank: Bank)`, `fun deleteBank(bank: Bank)`. Task 7's screen binds to these.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/ui/screens/banks/BankManagementViewModelTest.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.banks

import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.repository.BankRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BankManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BankRepository>()

    private val hdfc = Bank(id = 1, name = "HDFC Bank", smsSender = "HDFCBK")
    private val icici = Bank(id = 2, name = "ICICI Bank", smsSender = "ICICIB")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `banks flow emits repository list`() = runTest(testDispatcher) {
        every { repository.getAllBanks() } returns flowOf(listOf(hdfc, icici))
        every { repository.countTransactions(1L) } returns 0
        every { repository.countTransactions(2L) } returns 0
        val viewModel = BankManagementViewModel(repository)
        val job = launch { viewModel.banks.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(hdfc, icici), viewModel.banks.value)
        job.cancel()
    }

    @Test
    fun `addBank inserts with trimmed and uppercased sender`() = runTest(testDispatcher) {
        every { repository.getAllBanks() } returns flowOf(emptyList())
        coEvery { repository.insert(any()) } returns 3L
        val viewModel = BankManagementViewModel(repository)
        viewModel.addBank("  Axis Bank ", " axisb ")
        advanceUntilIdle()
        coVerify {
            repository.insert(Bank(id = 0, name = "Axis Bank", smsSender = "AXISB"))
        }
    }

    @Test
    fun `updateBank updates the bank`() = runTest(testDispatcher) {
        every { repository.getAllBanks() } returns flowOf(emptyList())
        coEvery { repository.update(any()) } returns Unit
        val viewModel = BankManagementViewModel(repository)
        val updated = hdfc.copy(name = "HDFC Bank Ltd")
        viewModel.updateBank(updated)
        advanceUntilIdle()
        coVerify { repository.update(updated) }
    }

    @Test
    fun `deleteBank deletes bank with zero transactions`() = runTest(testDispatcher) {
        every { repository.getAllBanks() } returns flowOf(emptyList())
        every { repository.countTransactions(1L) } returns 0
        coEvery { repository.delete(any()) } returns Unit
        val viewModel = BankManagementViewModel(repository)
        viewModel.deleteBank(hdfc)
        advanceUntilIdle()
        coVerify { repository.delete(hdfc) }
    }

    @Test
    fun `deleteBank guards bank with transactions`() = runTest(testDispatcher) {
        every { repository.getAllBanks() } returns flowOf(emptyList())
        every { repository.countTransactions(1L) } returns 3
        val viewModel = BankManagementViewModel(repository)
        viewModel.deleteBank(hdfc)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.delete(any()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.banks.BankManagementViewModelTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Write the ViewModel**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankManagementViewModel.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.banks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.repository.BankRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BankManagementViewModel @Inject constructor(
    private val repository: BankRepository
) : ViewModel() {

    val banks: StateFlow<List<Bank>> = repository.getAllBanks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _transactionCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val transactionCounts: StateFlow<Map<Long, Int>> = _transactionCounts.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllBanks().map { list ->
                list.associate { it.id to repository.countTransactions(it.id) }
            }.collect { _transactionCounts.value = it }
        }
    }

    fun addBank(name: String, smsSender: String) {
        viewModelScope.launch {
            repository.insert(Bank(id = 0, name = name.trim(), smsSender = smsSender.trim().uppercase()))
        }
    }

    fun updateBank(bank: Bank) {
        viewModelScope.launch {
            repository.update(bank)
        }
    }

    fun deleteBank(bank: Bank) {
        viewModelScope.launch {
            val count = repository.countTransactions(bank.id)
            if (count == 0) {
                repository.delete(bank)
            }
        }
    }
}
```

Note: the `init` block starts a collection that calls `countTransactions` per bank — every test that constructs the ViewModel must stub `countTransactions` (the tests above do). `deleteBank` also calls it, so the guard test stubs it too.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.banks.BankManagementViewModelTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Gate + commit**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankManagementViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/banks/BankManagementViewModelTest.kt
git commit -m "feat: add BankManagementViewModel with guarded bank deletion"
```

---

### Task 6: BankDetailViewModel

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDetailViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/banks/BankDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `BankRepository.getBankById(bankId)`; `SmsRuleRepository.getRulesForBank(bankId)`, `insert`, `update`, `delete` (Task 2).
- Produces: `BankDetailViewModel` with `val bank: StateFlow<Bank?>`, `val rules: StateFlow<List<SmsRule>>`, `fun addRule(description, pattern, isActive = true)`, `fun updateRule(rule)`, `fun deleteRule(rule)`, `fun setRuleActive(rule, active)`. Task 8's screen binds to these.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/ui/screens/banks/BankDetailViewModelTest.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.banks

import androidx.lifecycle.SavedStateHandle
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BankDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val bankRepository = mockk<BankRepository>()
    private val ruleRepository = mockk<SmsRuleRepository>()

    private val hdfc = Bank(id = 1, name = "HDFC Bank", smsSender = "HDFCBK")
    private val rule = SmsRule(
        id = 1L,
        bankId = 1L,
        pattern = "Spent Rs\\.([\\d,.]+) On HDFC Bank Card",
        description = "HDFC CC Debit",
        isActive = true
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: Map<String, Any> = mapOf("bankId" to 1L)) =
        BankDetailViewModel(SavedStateHandle(savedState), bankRepository, ruleRepository)

    @Test
    fun `bank flow emits bank by id`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(emptyList())
        val viewModel = viewModel()
        val job = launch { viewModel.bank.collect {} }
        advanceUntilIdle()
        assertEquals(hdfc, viewModel.bank.value)
        job.cancel()
    }

    @Test
    fun `rules flow emits rules for bank`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(listOf(rule))
        val viewModel = viewModel()
        val job = launch { viewModel.rules.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(rule), viewModel.rules.value)
        job.cancel()
    }

    @Test
    fun `addRule inserts with bank id and isActive true`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(emptyList())
        coEvery { ruleRepository.insert(any()) } returns 9L
        val viewModel = viewModel()
        viewModel.addRule("Axis UPI", "Acct \\w+ credited")
        advanceUntilIdle()
        coVerify {
            ruleRepository.insert(
                SmsRule(id = 0L, bankId = 1L, pattern = "Acct \\w+ credited", description = "Axis UPI", isActive = true)
            )
        }
    }

    @Test
    fun `updateRule updates the rule`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(emptyList())
        coEvery { ruleRepository.update(any()) } returns Unit
        val viewModel = viewModel()
        val updated = rule.copy(description = "HDFC Debit v2")
        viewModel.updateRule(updated)
        advanceUntilIdle()
        coVerify { ruleRepository.update(updated) }
    }

    @Test
    fun `deleteRule deletes the rule`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(emptyList())
        coEvery { ruleRepository.delete(any()) } returns Unit
        val viewModel = viewModel()
        viewModel.deleteRule(rule)
        advanceUntilIdle()
        coVerify { ruleRepository.delete(rule) }
    }

    @Test
    fun `setRuleActive flips isActive`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        every { ruleRepository.getRulesForBank(1L) } returns flowOf(emptyList())
        coEvery { ruleRepository.update(any()) } returns Unit
        val viewModel = viewModel()
        viewModel.setRuleActive(rule, false)
        advanceUntilIdle()
        coVerify { ruleRepository.update(rule.copy(isActive = false)) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.banks.BankDetailViewModelTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Write the ViewModel**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDetailViewModel.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.banks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BankDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bankRepository: BankRepository,
    private val smsRuleRepository: SmsRuleRepository
) : ViewModel() {

    private val bankId: Long = checkNotNull(savedStateHandle["bankId"])

    val bank: StateFlow<Bank?> = flow {
        emit(bankRepository.getBankById(bankId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val rules: StateFlow<List<SmsRule>> = smsRuleRepository.getRulesForBank(bankId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRule(description: String, pattern: String, isActive: Boolean = true) {
        viewModelScope.launch {
            smsRuleRepository.insert(
                SmsRule(id = 0L, bankId = bankId, pattern = pattern.trim(), description = description.trim(), isActive = isActive)
            )
        }
    }

    fun updateRule(rule: SmsRule) {
        viewModelScope.launch {
            smsRuleRepository.update(rule)
        }
    }

    fun deleteRule(rule: SmsRule) {
        viewModelScope.launch {
            smsRuleRepository.delete(rule)
        }
    }

    fun setRuleActive(rule: SmsRule, active: Boolean) {
        viewModelScope.launch {
            smsRuleRepository.update(rule.copy(isActive = active))
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.banks.BankDetailViewModelTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Gate + commit**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDetailViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/banks/BankDetailViewModelTest.kt
git commit -m "feat: add BankDetailViewModel for per-bank rule management"
```

---

### Task 7: Bank management screen + dialogs

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankManagementScreen.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDialog.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDeleteDialog.kt`

**Interfaces:**
- Consumes: `BankManagementViewModel` (Task 5): `banks`, `transactionCounts`, `addBank`, `updateBank`, `deleteBank`. `validateBankName`, `validateBankSender` (Task 4). `Bank` domain model.
- Produces: `@Composable fun BankManagementScreen(onBack: () -> Unit = {}, onBankClick: (Bank) -> Unit = {}, viewModel: BankManagementViewModel = hiltViewModel())`. Task 9 wires routes.

- [ ] **Step 1: Write BankDeleteDialog**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDeleteDialog.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.banks

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.smsexpensetracker.domain.model.Bank

@Composable
fun BankDeleteDialog(
    bank: Bank,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${bank.name}?") },
        text = { Text("This bank and its SMS rules will be removed.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 2: Write BankDialog**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDialog.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.banks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.ui.util.validateBankName
import com.smsexpensetracker.ui.util.validateBankSender

@Composable
fun BankDialog(
    existing: Bank?,
    allBanks: List<Bank>,
    onSave: (name: String, smsSender: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var sender by remember { mutableStateOf(existing?.smsSender ?: "") }

    val nameError = validateBankName(name, allBanks, existing?.id)
    val senderError = validateBankSender(sender)
    val isValid = nameError == null && senderError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add bank" else "Edit bank") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("Sender") },
                    isError = senderError != null,
                    supportingText = senderError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), sender.trim().uppercase()) },
                enabled = isValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 3: Write the screen**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankManagementScreen.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.banks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.domain.model.Bank
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankManagementScreen(
    onBack: () -> Unit = {},
    onBankClick: (Bank) -> Unit = {},
    viewModel: BankManagementViewModel = hiltViewModel()
) {
    val banks by viewModel.banks.collectAsState()
    val transactionCounts by viewModel.transactionCounts.collectAsState()
    var editing by remember { mutableStateOf<Bank?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Bank?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Banks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add bank")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(banks, key = { it.id }) { bank ->
                val count = transactionCounts[bank.id] ?: 0
                val canDelete = count == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBankClick(bank) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bank.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = bank.smsSender + if (count > 0) " · $count transactions" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { editing = bank }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${bank.name}")
                    }
                    IconButton(
                        onClick = {
                            if (canDelete) deleting = bank
                            else scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Cannot delete — $count transactions use this bank",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete ${bank.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        BankDialog(
            existing = null,
            allBanks = banks,
            onSave = { name, sender ->
                viewModel.addBank(name, sender)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    editing?.let { bank ->
        BankDialog(
            existing = bank,
            allBanks = banks,
            onSave = { name, sender ->
                viewModel.updateBank(bank.copy(name = name, smsSender = sender))
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { bank ->
        BankDeleteDialog(
            bank = bank,
            onConfirm = {
                viewModel.deleteBank(bank)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}
```

- [ ] **Step 4: Build + commit**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Fix any missing imports the compiler flags.

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankManagementScreen.kt app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDialog.kt app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDeleteDialog.kt
git commit -m "feat: add bank management screen with add/edit/guarded-delete dialogs"
```

---

### Task 8: Bank detail screen + rule dialogs

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDetailScreen.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleDialog.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleDeleteDialog.kt`

**Interfaces:**
- Consumes: `BankDetailViewModel` (Task 6): `bank`, `rules`, `addRule`, `updateRule`, `deleteRule`, `setRuleActive`. `validateRuleDescription`, `validatePattern` (Task 4). `Bank`, `SmsRule` models.
- Produces: `@Composable fun BankDetailScreen(onBack: () -> Unit = {}, viewModel: BankDetailViewModel = hiltViewModel())`. Task 9 wires the route.

- [ ] **Step 1: Write RuleDeleteDialog**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleDeleteDialog.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.banks

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.smsexpensetracker.domain.model.SmsRule

@Composable
fun RuleDeleteDialog(
    rule: SmsRule,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${rule.description}?") },
        text = { Text("This SMS rule will no longer be used to parse transactions.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 2: Write RuleDialog**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleDialog.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.banks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.ui.util.validatePattern
import com.smsexpensetracker.ui.util.validateRuleDescription

@Composable
fun RuleDialog(
    existing: SmsRule?,
    onSave: (description: String, pattern: String) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var pattern by remember { mutableStateOf(existing?.pattern ?: "") }

    val descriptionError = validateRuleDescription(description)
    val patternError = validatePattern(pattern)
    val isValid = descriptionError == null && patternError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add rule" else "Edit rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    isError = descriptionError != null,
                    supportingText = descriptionError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern (regex)") },
                    isError = patternError != null,
                    supportingText = patternError?.let { { Text(it) } },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(description.trim(), pattern.trim()) },
                enabled = isValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 3: Write the screen**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDetailScreen.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.banks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankDetailScreen(
    onBack: () -> Unit = {},
    viewModel: BankDetailViewModel = hiltViewModel()
) {
    val bank by viewModel.bank.collectAsState()
    val rules by viewModel.rules.collectAsState()
    var editing by remember { mutableStateOf<SmsRule?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SmsRule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bank?.name ?: "Bank") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add rule")
            }
        }
    ) { innerPadding ->
        if (rules.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Add,
                title = "No rules yet",
                subtitle = "Tap + to add an SMS rule for ${bank?.name ?: "this bank"}",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.description, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = rule.pattern,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = rule.isActive,
                            onCheckedChange = { viewModel.setRuleActive(rule, it) }
                        )
                        IconButton(onClick = { editing = rule }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${rule.description}")
                        }
                        IconButton(onClick = { deleting = rule }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete ${rule.description}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        RuleDialog(
            existing = null,
            onSave = { description, pattern ->
                viewModel.addRule(description, pattern)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    editing?.let { rule ->
        RuleDialog(
            existing = rule,
            onSave = { description, pattern ->
                viewModel.updateRule(rule.copy(description = description, pattern = pattern))
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { rule ->
        RuleDeleteDialog(
            rule = rule,
            onConfirm = {
                viewModel.deleteRule(rule)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}
```

- [ ] **Step 4: Build + commit**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Fix any missing imports the compiler flags.

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDetailScreen.kt app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleDialog.kt app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleDeleteDialog.kt
git commit -m "feat: add bank detail screen with rule management and enable toggles"
```

---

### Task 9: Wire navigation + Settings row

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `BankManagementScreen(onBack, onBankClick)` (Task 7), `BankDetailScreen(onBack)` (Task 8).
- Produces: routes `"banks"` and `"banks/{bankId}"`; `SettingsScreen(onNavigateToBanks: () -> Unit = {})`.

- [ ] **Step 1: Add routes**

Open `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt`. Add imports:
```kotlin
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.smsexpensetracker.ui.screens.banks.BankDetailScreen
import com.smsexpensetracker.ui.screens.banks.BankManagementScreen
```

Change the Settings composable and add the two routes:

```kotlin
        composable(BottomNavItem.Settings.route) {
            SettingsScreen(
                onNavigateToCategories = { navController.navigate("categories") },
                onNavigateToBanks = { navController.navigate("banks") }
            )
        }
        composable("categories") {
            CategoryManagementScreen(onBack = { navController.popBackStack() })
        }
        composable("banks") {
            BankManagementScreen(
                onBack = { navController.popBackStack() },
                onBankClick = { bank -> navController.navigate("banks/${bank.id}") }
            )
        }
        composable(
            route = "banks/{bankId}",
            arguments = listOf(navArgument("bankId") { type = NavType.LongType })
        ) {
            BankDetailScreen(onBack = { navController.popBackStack() })
        }
```

- [ ] **Step 2: Add Settings row**

Open `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`. Change the signature:

```kotlin
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToCategories: () -> Unit = {},
    onNavigateToBanks: () -> Unit = {}
) {
```

Add a "Banks & Rules" row below the Categories row in the "Data" section (after the Categories Row block, before its trailing `Spacer(modifier = Modifier.size(32.dp))`):

```kotlin
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToBanks)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Banks & Rules",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
```

Note: the leading icon for "Banks & Rules" is a chevron only (mirroring the drill-down affordance); it renders as a leading decorative arrow. If that looks odd, replace the leading `Icon` with a bank-style icon — but keep it simple: a leading chevron is acceptable since the row is a navigation affordance. (Or omit the leading icon entirely and keep just label + trailing chevron.)

- [ ] **Step 3: Build + full gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass (201 — 176 existing + 1 inactive-rule + 13 validation + 5 bank VM + 6 detail VM).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt
git commit -m "feat: wire navigation and Settings row for bank and rule management"
```

---

### Task 10: Update TODO.md

**Files:**
- Modify: `TODO.md`

- [ ] **Step 1: Mark bank/rule management progress**

In `TODO.md`, Task 14 section, change:

```markdown
- [ ] Implement bank management: list banks, add new, edit sender pattern
- [ ] Implement SMS rule management per bank: list, add, edit, delete, enable/disable rules
  - [ ] **Add new bank rules** — user can add a regex rule (bank, `contentRegex` pattern, description) for a new SMS format/bank, not just edit seeded rules; new rules must be picked up by `ParserEngine` (Parser Test screen) without a rebuild
```

to:

```markdown
- [x] Implement bank management: list banks, add new, edit sender pattern
- [x] Implement SMS rule management per bank: list, add, edit, delete, enable/disable rules
  - [x] **Add new bank rules** — user can add a regex rule (bank, `contentRegex` pattern, description) for a new SMS format/bank, not just edit seeded rules; new rules must be picked up by `ParserEngine` (Parser Test screen) without a rebuild
```

- [ ] **Step 2: Commit**

```bash
git add TODO.md
git commit -m "docs: mark bank and rule management complete in TODO"
```

---

## Self-Review

**1. Spec coverage:**
- Schema v5 + migration + device test (spec §4) → Task 1.
- Repo write methods + count query (spec §5) → Task 2.
- Parser active filtering (spec §6) → Task 3.
- Validation helpers (spec §9) → Task 4.
- BankManagementViewModel (spec §7) → Task 5.
- BankDetailViewModel (spec §7) → Task 6.
- Bank screens/dialogs (spec §8) → Task 7.
- Rule screens/dialogs (spec §8) → Task 8.
- Navigation + Settings row (spec §10) → Task 9.
- TODO.md (spec §12) → Task 10.
- Verification (spec §13) → gate after every task.

**2. Placeholder scan:** No TBD/TODO/incomplete steps; all code blocks complete. One flagged decision (count query in BankDao vs TransactionDao) is resolved with explicit options in Task 2 Step 5, not left open.

**3. Type consistency:** `SmsRule(id, bankId, pattern, description, isActive)` defined Task 2, used identically Tasks 3/6/8. `BankRepository.countTransactions(bankId): Int` defined Task 2, used Task 5. `BankManagementViewModel.{banks,transactionCounts,addBank,updateBank,deleteBank}` defined Task 5, used Task 7. `BankDetailViewModel.{bank,rules,addRule,updateRule,deleteRule,setRuleActive}` defined Task 6, used Task 8. Routes `"banks"`/`"banks/{bankId}"` and `onNavigateToBanks` defined Task 9 only. Validators from Task 4 used in Tasks 7/8.
