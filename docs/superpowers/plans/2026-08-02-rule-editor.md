# Rule Editor Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the raw-regex rule dialog with a full-screen editor (Parser-Test-style) that tests a pattern against a pasted sample SMS via the real parser before saving.

**Architecture:** New `RuleEditorScreen` + `RuleEditorViewModel` (route `banks/{bankId}/rules/edit?ruleId={ruleId}`, `ruleId == -1` = add). The ViewModel holds draft state and runs the pure `RegexParser.parse` synchronously on Test tap. Save requires a successful match, then inserts/updates via `SmsRuleRepository` and pops back.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, Hilt, Room, MockK, `kotlinx-coroutines-test` (JUnit 4).

## Global Constraints

- Package: `com.smsexpensetracker`; min SDK 28 / target 36 / compile 37.
- No code comments unless the user asks for them.
- All amounts as paisa `Long` — never `Double`/`BigDecimal`. `parsePaisa` in `core/parser/Paisa.kt`.
- MockK for mocks; `runTest` + `StandardTestDispatcher` for Flow/suspend; `Dispatchers.setMain` in `@Before`, `resetMain` in `@After`.
- Test conventions: mirror `BankDetailViewModelTest.kt` exactly (dispatcher setup, `mockk<>` fields, `viewModel(savedState)` factory, `coEvery` for suspend, `every` for non-suspend, `coVerify` for suspend calls).
- Build gate: `./gradlew testDebugUnitTest assembleDebug` must be green before each commit.
- No `lint`/`typecheck` configured — build + test only.
- `RegexParser.parse(smsBody: String, pattern: String, bankId: Long): RegexMatch?` where `RegexMatch(amount: Long, description: String, bankId: Long, rawSms: String)` — returns null on no match OR if group 1 isn't a parseable amount.
- `SmsRuleRepository` already provides: `getRulesForBank(bankId): Flow<List<SmsRule>>`, `getRuleById(id): SmsRule?`, `insert(rule): Long`, `update(rule)`.
- `BankRepository.getBankById(bankId): SmsRule?` → `Flow`-free suspend returning `Bank?`.
- Validators (already exist, `ui/util/BankRulesValidation.kt`): `validatePattern(pattern): String?`, `validateRuleDescription(description): String?`.
- Spec: `docs/superpowers/specs/2026-08-02-rule-editor-design.md`.

---

### Task 1: RuleEditorViewModel

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `BankRepository.getBankById(bankId)`, `SmsRuleRepository.getRuleById(id)`, `SmsRuleRepository.insert(rule): Long`, `SmsRuleRepository.update(rule)`, `RegexParser.parse(...)`, `SmsRule` domain model, `RegexMatch`.
- Produces: `RuleEditorViewModel(savedStateHandle, bankRepository, smsRuleRepository)`. State `RuleEditorUiState`, exposed via `val uiState: StateFlow<RuleEditorUiState>`. Functions: `onSampleSmsChange(String)`, `onPatternChange(String)`, `onDescriptionChange(String)`, `onTest()`, `onSave()`, `consumeSaveError()`. Also `val bank: StateFlow<Bank?>`.
- `RuleEditorUiState` fields: `sampleSms: String = ""`, `draftPattern: String = ""`, `description: String = ""`, `testResult: RegexMatch? = null`, `hasTested: Boolean = false`, `saved: Boolean = false`, `saveError: String? = null`.

- [ ] **Step 1: Write the failing test**

Create `RuleEditorViewModelTest.kt`:

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val bankRepository = mockk<BankRepository>()
    private val ruleRepository = mockk<SmsRuleRepository>()

    private val hdfc = Bank(id = 1, name = "HDFC Bank", smsSender = "HDFCBK")
    private val existingRule = SmsRule(
        id = 7L,
        bankId = 1L,
        pattern = "Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
        description = "HDFC CC Debit",
        isActive = true
    )
    private val smsBody = "Spent Rs.1250.50 On HDFC Bank Card 1234 At Coffee Shop On 01-Aug"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        savedState: Map<String, Any> = mapOf("bankId" to 1L, "ruleId" to -1L)
    ) = RuleEditorViewModel(SavedStateHandle(savedState), bankRepository, ruleRepository)

    @Test
    fun `bank flow emits bank by id`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        val job = launch { vm.bank.collect {} }
        advanceUntilIdle()
        assertEquals(hdfc, vm.bank.value)
        job.cancel()
    }

    @Test
    fun `add mode starts with empty fields`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals("", state.sampleSms)
        assertEquals("", state.draftPattern)
        assertEquals("", state.description)
        assertNull(state.testResult)
        assertFalse(state.hasTested)
    }

    @Test
    fun `edit mode pre-fills pattern and description from existing rule`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        coEvery { ruleRepository.getRuleById(7L) } returns existingRule
        val vm = viewModel(mapOf("bankId" to 1L, "ruleId" to 7L))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(existingRule.pattern, state.draftPattern)
        assertEquals(existingRule.description, state.description)
    }

    @Test
    fun `test with matching pattern sets testResult`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange(existingRule.pattern)
        vm.onTest()
        val state = vm.uiState.value
        assertTrue(state.hasTested)
        assertNotNull(state.testResult)
        assertEquals(125050L, state.testResult?.amount)
        assertEquals("Coffee Shop", state.testResult?.description)
    }

    @Test
    fun `test with non-matching pattern sets hasTested true and testResult null`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange("ICICI Bank Acct 1234 debited for Rs 500.00 on 01-Aug; Swiggy credited. UPI")
        vm.onPatternChange(existingRule.pattern)
        vm.onTest()
        val state = vm.uiState.value
        assertTrue(state.hasTested)
        assertNull(state.testResult)
    }

    @Test
    fun `test with pattern whose group 1 is not an amount returns no match`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange("Spent (abc) On HDFC Bank Card \\d{4} At (.+?) On .+")
        vm.onTest()
        val state = vm.uiState.value
        assertTrue(state.hasTested)
        assertNull(state.testResult)
    }

    @Test
    fun `changing sample clears previous test result`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange(existingRule.pattern)
        vm.onTest()
        assertTrue(vm.uiState.value.hasTested)
        vm.onSampleSmsChange("changed")
        val state = vm.uiState.value
        assertFalse(state.hasTested)
        assertNull(state.testResult)
    }

    @Test
    fun `onSave in add mode inserts rule with trimmed values`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        coEvery { ruleRepository.insert(any()) } returns 9L
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange("  Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+  ")
        vm.onDescriptionChange("  HDFC CC Debit  ")
        vm.onSave()
        advanceUntilIdle()
        coVerify {
            ruleRepository.insert(
                SmsRule(
                    id = 0L,
                    bankId = 1L,
                    pattern = "Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
                    description = "HDFC CC Debit",
                    isActive = true
                )
            )
        }
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `onSave in edit mode updates existing rule preserving id and isActive`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        coEvery { ruleRepository.getRuleById(7L) } returns existingRule
        coEvery { ruleRepository.update(any()) } returns Unit
        val vm = viewModel(mapOf("bankId" to 1L, "ruleId" to 7L))
        advanceUntilIdle()
        vm.onDescriptionChange("HDFC CC Debit v2")
        vm.onSave()
        advanceUntilIdle()
        coVerify {
            ruleRepository.update(
                existingRule.copy(description = "HDFC CC Debit v2")
            )
        }
    }

    @Test
    fun `save failure sets saveError`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        coEvery { ruleRepository.insert(any()) } throws RuntimeException("db down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange(existingRule.pattern)
        vm.onDescriptionChange("HDFC CC Debit")
        vm.onSave()
        advanceUntilIdle()
        assertEquals("Could not save rule. Please try again.", vm.uiState.value.saveError)
        assertFalse(vm.uiState.value.saved)
    }

    @Test
    fun `consumeSaveError clears saveError`() = runTest(testDispatcher) {
        coEvery { bankRepository.getBankById(1L) } returns hdfc
        coEvery { ruleRepository.insert(any()) } throws RuntimeException("db down")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSampleSmsChange(smsBody)
        vm.onPatternChange(existingRule.pattern)
        vm.onDescriptionChange("HDFC CC Debit")
        vm.onSave()
        advanceUntilIdle()
        vm.consumeSaveError()
        assertNull(vm.uiState.value.saveError)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.banks.RuleEditorViewModelTest"`
Expected: COMPILATION FAILURE — `RuleEditorViewModel` not defined.

- [ ] **Step 3: Write the ViewModel**

Create `RuleEditorViewModel.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.banks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.core.parser.RegexMatch
import com.smsexpensetracker.core.parser.RegexParser
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleEditorUiState(
    val sampleSms: String = "",
    val draftPattern: String = "",
    val description: String = "",
    val testResult: RegexMatch? = null,
    val hasTested: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null
)

@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bankRepository: BankRepository,
    private val smsRuleRepository: SmsRuleRepository
) : ViewModel() {

    private val bankId: Long = checkNotNull(savedStateHandle["bankId"])
    private val ruleId: Long? = savedStateHandle["ruleId"].takeIf { it != -1L }
    private var existingRule: SmsRule? = null

    private val _uiState = MutableStateFlow(RuleEditorUiState())
    val uiState: StateFlow<RuleEditorUiState> = _uiState.asStateFlow()

    val bank: StateFlow<Bank?> = kotlinx.coroutines.flow.flow {
        emit(bankRepository.getBankById(bankId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            val existing = ruleId?.let { smsRuleRepository.getRuleById(it) }
            existingRule = existing
            if (existing != null) {
                _uiState.update {
                    it.copy(draftPattern = existing.pattern, description = existing.description)
                }
            }
        }
    }

    fun onSampleSmsChange(value: String) = _uiState.update {
        it.copy(sampleSms = value, testResult = null, hasTested = false)
    }

    fun onPatternChange(value: String) = _uiState.update {
        it.copy(draftPattern = value, testResult = null, hasTested = false)
    }

    fun onDescriptionChange(value: String) = _uiState.update {
        it.copy(description = value)
    }

    fun onTest() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                testResult = RegexParser.parse(state.sampleSms, state.draftPattern, bankId),
                hasTested = true
            )
        }
    }

    fun onSave() {
        val state = _uiState.value
        val existing = existingRule
        if (state.saved) return
        viewModelScope.launch {
            try {
                if (existing == null) {
                    smsRuleRepository.insert(
                        SmsRule(
                            id = 0L,
                            bankId = bankId,
                            pattern = state.draftPattern.trim(),
                            description = state.description.trim(),
                            isActive = true
                        )
                    )
                } else {
                    smsRuleRepository.update(
                        existing.copy(
                            pattern = state.draftPattern.trim(),
                            description = state.description.trim()
                        )
                    )
                }
                _uiState.update { it.copy(saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(saveError = "Could not save rule. Please try again.") }
            }
        }
    }

    fun consumeSaveError() = _uiState.update { it.copy(saveError = null) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.banks.RuleEditorViewModelTest"`
Expected: 11 tests pass, 0 failures.

- [ ] **Step 5: Run full build gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass (202 existing + 11 new = 213).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/banks/RuleEditorViewModelTest.kt
git commit -m "feat: add RuleEditorViewModel with live regex testing"
```

---

### Task 2: RuleEditorScreen

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleEditorScreen.kt`

**Interfaces:**
- Consumes: `RuleEditorViewModel` (from Task 1) with `uiState: StateFlow<RuleEditorUiState>`, `bank: StateFlow<Bank?>`, functions `onSampleSmsChange/onPatternChange/onDescriptionChange/onTest/onSave/consumeSaveError`. Validators `validatePattern`, `validateRuleDescription`. `formatPaisa(amount: Long): String` from `ui/util` (check import path used by ParserScreen: `com.smsexpensetracker.ui.util.formatPaisa`).
- Produces: `RuleEditorScreen(onBack: () -> Unit, onSaved: () -> Unit, viewModel: RuleEditorViewModel = hiltViewModel())`. Callers check `uiState.value.saved` via `LaunchedEffect` and invoke `onSaved()`.

- [ ] **Step 1: Write the composable**

Create `RuleEditorScreen.kt` (UI-only, no unit tests exist for screens in this codebase; gate is compile + manual smoke):

```kotlin
package com.smsexpensetracker.ui.screens.banks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.ui.util.formatPaisa
import com.smsexpensetracker.ui.util.validatePattern
import com.smsexpensetracker.ui.util.validateRuleDescription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RuleEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val bank by viewModel.bank.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var examplesExpanded by remember { mutableStateOf(false) }

    val descriptionError = validateRuleDescription(state.description)
    val patternError = validatePattern(state.draftPattern)
    val hasMatch = state.testResult != null
    val canSave = descriptionError == null && patternError == null && hasMatch

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    LaunchedEffect(state.saveError) {
        val error = state.saveError
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.consumeSaveError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Rule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::onSave, enabled = canSave) {
                        Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (bank != null) {
                Text(
                    text = bank!!.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = state.sampleSms,
                onValueChange = viewModel::onSampleSmsChange,
                label = { Text("Sample SMS") },
                supportingText = { Text("Paste a real bank SMS to test your pattern") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(28.dp),
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )

            OutlinedTextField(
                value = state.draftPattern,
                onValueChange = viewModel::onPatternChange,
                label = { Text("Pattern (regex)") },
                isError = patternError != null && state.draftPattern.isNotEmpty(),
                supportingText = {
                    Column {
                        if (patternError != null) {
                            Text(patternError)
                        }
                        Text("Group 1 = amount, Group 2 = description")
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(28.dp),
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(onClick = { examplesExpanded = !examplesExpanded }) {
                Text(if (examplesExpanded) "How it works & examples (hide)" else "How it works & examples")
            }
            AnimatedVisibility(visible = examplesExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "The pattern is a regular expression. Group 1 must capture the amount " +
                            "(e.g. 1250.50) and group 2 the description. Test against a real SMS " +
                            "before saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "UPI debit:\nICICI Bank Acct \\w+ debited for Rs ([\\d,.]+) on [\\d-]+; (.+?) credited\\. UPI",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Card spend:\nSpent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "UPI credit:\nAcct \\w+ is credited with Rs ([\\d,.]+) on [\\d-]+ from (.+?)\\. UPI",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description") },
                isError = descriptionError != null && state.description.isNotEmpty(),
                supportingText = descriptionError?.let { { Text(it) } },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = viewModel::onTest,
                enabled = state.sampleSms.isNotBlank() && patternError == null,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test")
            }

            if (state.hasTested) {
                if (hasMatch) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Matches", style = MaterialTheme.typography.titleSmall)
                            RuleEditorResultField("Amount", formatPaisa(state.testResult!!.amount))
                            RuleEditorResultField("Description", state.testResult!!.description)
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No match for this SMS",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleEditorResultField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleEditorScreen.kt
git commit -m "feat: add rule editor screen with live test feedback"
```

---

### Task 3: Navigation wiring and RuleDialog removal

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDetailScreen.kt`
- Delete: `app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleDialog.kt`

**Interfaces:**
- Consumes: `RuleEditorScreen(onBack, onSaved)` from Task 2. `BankDetailScreen` callbacks `onAddRule: () -> Unit` and `onEditRule: (Long) -> Unit` (new params, wired in NavGraph).
- Produces: route `"banks/{bankId}/rules/edit?ruleId={ruleId}"` (`bankId` LongType, `ruleId` LongType default `-1L`). BankDetailScreen no longer shows add/edit dialogs; keeps delete dialog.

- [ ] **Step 1: Update BankDetailScreen**

Remove the `RuleDialog` import, the `var editing by remember ...`, the `var showAdd by remember ...` states, the two `RuleDialog` invocations (`showAdd` and `editing?.let`), and the FAB now calls `onAddRule()`.

Add new params to the signature:

```kotlin
fun BankDetailScreen(
    onBack: () -> Unit = {},
    onAddRule: () -> Unit = {},
    onEditRule: (Long) -> Unit = {},
    viewModel: BankDetailViewModel = hiltViewModel()
)
```

- FAB: `FloatingActionButton(onClick = onAddRule)`.
- Edit `IconButton` (`onClick = { editing = rule }`): replace with `onClick = { onEditRule(rule.id) }`.
- Keep the `deleting`/`RuleDeleteDialog` block unchanged.
- Keep `showAdd`/`editing` state variables only if referenced elsewhere — they should be fully removed.

- [ ] **Step 2: Update NavGraph**

Import `RuleEditorScreen`. Replace the `banks/{bankId}` block's `BankDetailScreen(onBack = ...)` so it reads the `bankId` nav argument and wires the new callbacks:

```kotlin
composable(
    route = "banks/{bankId}",
    arguments = listOf(navArgument("bankId") { type = NavType.LongType })
) { entry ->
    val bankId = entry.arguments?.getLong("bankId")
    BankDetailScreen(
        onBack = { navController.popBackStack() },
        onAddRule = { bankId?.let { navController.navigate("banks/$it/rules/edit") } },
        onEditRule = { ruleId -> bankId?.let { navController.navigate("banks/$it/rules/edit?ruleId=$ruleId") } }
    )
}
```

Add a new destination for the editor (note `ruleId` has `defaultValue = -1L`, which the ViewModel interprets as add mode):

```kotlin
composable(
    route = "banks/{bankId}/rules/edit?ruleId={ruleId}",
    arguments = listOf(
        navArgument("bankId") { type = NavType.LongType },
        navArgument("ruleId") { type = NavType.LongType; defaultValue = -1L }
    )
) {
    RuleEditorScreen(
        onBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() }
    )
}
```

- [ ] **Step 3: Delete RuleDialog.kt**

Run: `rm app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleDialog.kt`

- [ ] **Step 4: Verify build + tests**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, all 213 tests pass.

- [ ] **Step 5: Verify no dangling references**

Run: `grep -rn "RuleDialog" app/src/main app/src/test`
Expected: no matches (RuleDeleteDialog is a different file — keep it).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt app/src/main/java/com/smsexpensetracker/ui/screens/banks/BankDetailScreen.kt
git rm app/src/main/java/com/smsexpensetracker/ui/screens/banks/RuleDialog.kt
git commit -m "feat: wire rule editor navigation and remove old rule dialog"
```

---

### Task 4: Spec verification and TODO update

**Files:**
- Modify: `TODO.md`

- [ ] **Step 1: Run the full gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 213 tests, 0 failures.

- [ ] **Step 2: Manual smoke checklist** (device/emulator via `scripts/push_test_sms.sh` or paste):
1. Settings → Banks & Rules → tap a bank → FAB → editor opens with empty fields, Test disabled until SMS pasted.
2. Paste a real HDFC debit SMS + the card-spend example pattern → Test → "Matches" card shows amount + description; Save enabled → Save → back in bank detail, rule listed.
3. Edit that rule (pencil) → fields pre-filled → change pattern to something that won't match → Test → red "No match" card → Save disabled.
4. Pattern with non-numeric group 1 → no match (contract enforced).
5. Bad regex (e.g. `(`) → inline syntax error, Test disabled, Save disabled.

- [ ] **Step 3: Update TODO.md**

In the Settings section (Task 14), update the SMS rule management bullet to reflect the editor. Add a line under the bank/rules bullet:

```
- [x] Rule editor tests pattern against a pasted sample SMS before saving
```

Place it as a sub-bullet of the existing `SMS rule management per bank` bullet.

- [ ] **Step 4: Commit**

```bash
git add TODO.md
git commit -m "docs: mark rule editor complete in TODO"
```

---

## Self-Review Notes

- Spec §2 (save requires successful test): Task 1 test `onSave in add mode...` doesn't gate on `hasTested` — the UI (`canSave`) enforces it. Correct separation: ViewModel stores what it's told; the screen disables Save until a match exists. Covered in Task 2 `canSave`.
- Spec §5 `testResult null = no match` + `hasTested` flag: implemented in Task 1; screen shows the red card only when `hasTested && testResult == null`.
- Spec §8 "Test disabled when sample blank or syntax invalid": Task 2 Button `enabled` matches.
- Spec §9 test list: all 11 ViewModel tests present in Task 1.
- `formatPaisa` import path confirmed via ParserScreen.kt:52 (`com.smsexpensetracker.ui.util.formatPaisa`).
- Task 3 Step 2 catches a navigation lambda pitfall (`it` binding) — the correct `entry`-based version is shown.
