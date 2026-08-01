# Category Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add full CRUD for expense categories behind a new Settings → Categories screen, with seeded-category delete protection, name validation, and color/icon pickers.

**Architecture:** Follows the app's established MVVM pattern: extend `CategoryRepository` with write methods (DAO already has them), add a new `CategoryManagementViewModel` + `CategoryManagementScreen`/dialogs under `ui/screens/categories/`, register a `categories` navigation route, and add a drill-down row in `SettingsScreen`. No Room schema change.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Hilt, Room, MockK, kotlinx-coroutines-test (`runTest`, `StandardTestDispatcher`).

## Global Constraints

- All amounts are paisa `Long` — not relevant here, but do not introduce `Double`/`BigDecimal` anywhere.
- Do NOT add comments to code unless asked.
- Follow existing patterns: `@HiltViewModel`, `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`, `viewModelScope.launch`, `Dispatchers.setMain(testDispatcher)` in tests.
- No Robolectric; unit tests only via MockK + `runTest`.
- Gate after every task: `./gradlew testDebugUnitTest assembleDebug` must pass. If KSP/Hilt acts up, run `./gradlew clean` first.
- `material-icons-core` and `material-icons-extended` (both 1.7.8) are available — all 14 icon names in §10 of the spec verified present.
- Spec: `docs/superpowers/specs/2026-08-01-category-management-design.md`.

---

### Task 1: Repository write methods

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/domain/repository/CategoryRepository.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/CategoryRepositoryImpl.kt`

**Interfaces:**
- Consumes: `CategoryDao.insert(category: CategoryEntity): Long`, `CategoryDao.update(category: CategoryEntity)`, `CategoryDao.delete(category: CategoryEntity)` — all already exist; `CategoryEntity`, `Category` domain model.
- Produces: `CategoryRepository.insert(category: Category): Long`, `CategoryRepository.update(category: Category)`, `CategoryRepository.delete(category: Category)`. Later tasks call these via the ViewModel.

- [ ] **Step 1: Add write methods to the repository interface**

Open `app/src/main/java/com/smsexpensetracker/domain/repository/CategoryRepository.kt` and add after the existing `getCategoryById`:

```kotlin
    suspend fun insert(category: Category): Long

    suspend fun update(category: Category)

    suspend fun delete(category: Category)
```

- [ ] **Step 2: Implement in the impl**

Open `app/src/main/java/com/smsexpensetracker/data/repository/CategoryRepositoryImpl.kt`. Add `import com.smsexpensetracker.core.database.entity.CategoryEntity` if not already imported (it is). Add these methods and a private mapper:

```kotlin
    override suspend fun insert(category: Category): Long =
        categoryDao.insert(category.toEntity())

    override suspend fun update(category: Category) {
        categoryDao.update(category.toEntity())
    }

    override suspend fun delete(category: Category) {
        categoryDao.delete(category.toEntity())
    }

    private fun Category.toEntity() = CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isDefault = isDefault
    )
```

- [ ] **Step 3: Build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, existing tests pass (165).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/repository/CategoryRepository.kt app/src/main/java/com/smsexpensetracker/data/repository/CategoryRepositoryImpl.kt
git commit -m "feat: add write methods to CategoryRepository"
```

---

### Task 2: Icon mapping + name validation helper

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/util/CategoryIcons.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/util/CategoryValidationTest.kt`

**Interfaces:**
- Consumes: Material icons from `androidx.compose.material.icons.filled.*`; `androidx.compose.ui.graphics.vector.ImageVector`.
- Produces: `fun materialIcon(name: String): ImageVector`, `val CATEGORY_COLORS: List<Int>`, `val CATEGORY_ICON_NAMES: List<String>`, `fun validateCategoryName(name: String, existing: List<Category>, editingId: Long?): String?`. The ViewModel/UI in Task 4 and Task 5 consume these.

- [ ] **Step 1: Write the failing validation test**

Create `app/src/test/java/com/smsexpensetracker/ui/util/CategoryValidationTest.kt`:

```kotlin
package com.smsexpensetracker.ui.util

import com.smsexpensetracker.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryValidationTest {

    private val existing = listOf(
        Category(id = 1, name = "Food & Dining", icon = "restaurant", color = -13108, isDefault = true),
        Category(id = 2, name = "Groceries", icon = "shopping_cart", color = -13956304, isDefault = true)
    )

    @Test
    fun `blank name is rejected`() {
        assertEquals("Name is required", validateCategoryName("   ", existing, null))
    }

    @Test
    fun `blank name is rejected when editing`() {
        assertEquals("Name is required", validateCategoryName("", existing, 1))
    }

    @Test
    fun `too long name is rejected`() {
        assertEquals(
            "Name must be 30 characters or fewer",
            validateCategoryName("x".repeat(31), existing, null)
        )
    }

    @Test
    fun `duplicate name is rejected case-insensitively`() {
        assertEquals(
            "A category with this name already exists",
            validateCategoryName("food & dining", existing, null)
        )
    }

    @Test
    fun `same name as self when editing is allowed`() {
        assertNull(validateCategoryName("food & dining", existing, 1))
    }

    @Test
    fun `unique name is allowed`() {
        assertNull(validateCategoryName("Coffee", existing, null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.util.CategoryValidationTest"`
Expected: FAIL — `validateCategoryName` unresolved (won't compile).

- [ ] **Step 3: Write the helper**

Create `app/src/main/java/com/smsexpensetracker/ui/util/CategoryIcons.kt`:

```kotlin
package com.smsexpensetracker.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.smsexpensetracker.domain.model.Category

val CATEGORY_COLORS = listOf(
    -13108, -13956304, -48060, -13676760, -10496, -16581634,
    -14513374, -12664161, -4880347, -7084816, -7829368, -10980385
)

val CATEGORY_ICON_NAMES = listOf(
    "restaurant", "shopping_cart", "local_gas_station", "receipt",
    "shopping_bag", "movie", "local_hospital", "directions_car",
    "school", "home", "flight", "payments", "trending_up", "category"
)

fun materialIcon(name: String): ImageVector = when (name) {
    "restaurant" -> Icons.Filled.Restaurant
    "shopping_cart" -> Icons.Filled.ShoppingCart
    "local_gas_station" -> Icons.Filled.LocalGasStation
    "receipt" -> Icons.Filled.Receipt
    "shopping_bag" -> Icons.Filled.ShoppingBag
    "movie" -> Icons.Filled.Movie
    "local_hospital" -> Icons.Filled.LocalHospital
    "directions_car" -> Icons.Filled.DirectionsCar
    "school" -> Icons.Filled.School
    "home" -> Icons.Filled.Home
    "flight" -> Icons.Filled.Flight
    "payments" -> Icons.Filled.Payments
    "trending_up" -> Icons.Filled.TrendingUp
    "category" -> Icons.Filled.Category
    else -> Icons.Filled.Category
}

fun validateCategoryName(name: String, existing: List<Category>, editingId: Long?): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "Name is required"
    if (trimmed.length > 30) return "Name must be 30 characters or fewer"
    val duplicate = existing.any { it.id != editingId && it.name.equals(trimmed, ignoreCase = true) }
    if (duplicate) return "A category with this name already exists"
    return null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.util.CategoryValidationTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/util/CategoryIcons.kt app/src/test/java/com/smsexpensetracker/ui/util/CategoryValidationTest.kt
git commit -m "feat: add category icon mapping and name validation helper"
```

---

### Task 3: CategoryManagementViewModel

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryManagementViewModel.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/screens/categories/CategoryManagementViewModelTest.kt`

**Interfaces:**
- Consumes: `CategoryRepository` (read + write from Tasks 1); `Category` domain model.
- Produces: `CategoryManagementViewModel` with `val categories: StateFlow<List<Category>>`, `fun addCategory(name: String, icon: String, color: Int)`, `fun updateCategory(category: Category)`, `fun deleteCategory(category: Category)`. Task 4's screen binds to these.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/ui/screens/categories/CategoryManagementViewModelTest.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.categories

import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.repository.CategoryRepository
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
class CategoryManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<CategoryRepository>()

    private val food = Category(id = 1, name = "Food", icon = "restaurant", color = -13108, isDefault = true)
    private val coffee = Category(id = 2, name = "Coffee", icon = "local_cafe", color = -10496, isDefault = false)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `categories flow emits repository list`() = runTest(testDispatcher) {
        every { repository.getAllCategories() } returns flowOf(listOf(food, coffee))
        val viewModel = CategoryManagementViewModel(repository)
        val job = launch { viewModel.categories.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(food, coffee), viewModel.categories.value)
        job.cancel()
    }

    @Test
    fun `addCategory inserts with isDefault false`() = runTest(testDispatcher) {
        coEvery { repository.insert(any()) } returns 3L
        val viewModel = CategoryManagementViewModel(repository)
        viewModel.addCategory("Travel", "flight", -13676760)
        advanceUntilIdle()
        coVerify {
            repository.insert(
                Category(id = 0, name = "Travel", icon = "flight", color = -13676760, isDefault = false)
            )
        }
    }

    @Test
    fun `updateCategory updates the category`() = runTest(testDispatcher) {
        coEvery { repository.update(any()) } returns Unit
        val viewModel = CategoryManagementViewModel(repository)
        val updated = coffee.copy(name = "Cafe")
        viewModel.updateCategory(updated)
        advanceUntilIdle()
        coVerify { repository.update(updated) }
    }

    @Test
    fun `deleteCategory deletes non-default category`() = runTest(testDispatcher) {
        coEvery { repository.delete(any()) } returns Unit
        val viewModel = CategoryManagementViewModel(repository)
        viewModel.deleteCategory(coffee)
        advanceUntilIdle()
        coVerify { repository.delete(coffee) }
    }

    @Test
    fun `deleteCategory guards seeded category`() = runTest(testDispatcher) {
        val viewModel = CategoryManagementViewModel(repository)
        viewModel.deleteCategory(food)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.delete(any()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.categories.CategoryManagementViewModelTest"`
Expected: FAIL — class not found (won't compile).

- [ ] **Step 3: Write the ViewModel**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryManagementViewModel.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val repository: CategoryRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String, icon: String, color: Int) {
        viewModelScope.launch {
            repository.insert(Category(id = 0, name = name.trim(), icon = icon, color = color, isDefault = false))
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.update(category)
        }
    }

    fun deleteCategory(category: Category) {
        if (category.isDefault) return
        viewModelScope.launch {
            repository.delete(category)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.screens.categories.CategoryManagementViewModelTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Gate + commit**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryManagementViewModel.kt app/src/test/java/com/smsexpensetracker/ui/screens/categories/CategoryManagementViewModelTest.kt
git commit -m "feat: add CategoryManagementViewModel with seeded-category delete guard"
```

---

### Task 4: Category management screen + dialogs

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryManagementScreen.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryDialog.kt`
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryDeleteDialog.kt`

**Interfaces:**
- Consumes: `CategoryManagementViewModel` (Task 3): `categories`, `addCategory`, `updateCategory`, `deleteCategory`. `materialIcon`, `CATEGORY_COLORS`, `CATEGORY_ICON_NAMES`, `validateCategoryName` (Task 2). `Category` domain model.
- Produces: `@Composable fun CategoryManagementScreen(onBack: () -> Unit = {}, viewModel: CategoryManagementViewModel = hiltViewModel())`. Task 5 wires the route.

- [ ] **Step 1: Write the dialogs**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryDeleteDialog.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.categories

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.smsexpensetracker.domain.model.Category

@Composable
fun CategoryDeleteDialog(
    category: Category,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${category.name}?") },
        text = { Text("Transactions in this category will become uncategorized.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

Create `app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryDialog.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.ui.util.CATEGORY_COLORS
import com.smsexpensetracker.ui.util.CATEGORY_ICON_NAMES
import com.smsexpensetracker.ui.util.materialIcon
import com.smsexpensetracker.ui.util.validateCategoryName

@Composable
fun CategoryDialog(
    existing: Category?,
    allCategories: List<Category>,
    onSave: (name: String, icon: String, color: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: CATEGORY_ICON_NAMES.first()) }
    var color by remember { mutableStateOf(existing?.color ?: CATEGORY_COLORS.first()) }

    val nameError = validateCategoryName(name, allCategories, existing?.id)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add category" else "Edit category") },
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
                Column {
                    Text("Color", style = MaterialTheme.typography.labelLarge)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        items(CATEGORY_COLORS) { value ->
                            val selected = value == color
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(value))
                                    .then(
                                        if (selected) Modifier.border(
                                            BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                                            CircleShape
                                        ) else Modifier
                                    )
                                    .clickable { color = value }
                            )
                        }
                    }
                }
                Column {
                    Text("Icon", style = MaterialTheme.typography.labelLarge)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        items(CATEGORY_ICON_NAMES) { name ->
                            val selected = name == icon
                            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(
                                imageVector = materialIcon(name),
                                contentDescription = name,
                                tint = tint,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { icon = name }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), icon, color) },
                enabled = nameError == null
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

Note: this uses `Box` inside the color grid — add `import androidx.compose.foundation.layout.Box` if the compiler flags it (it is used, so include it).

- [ ] **Step 2: Write the screen**

Create `app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryManagementScreen.kt`:

```kotlin
package com.smsexpensetracker.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.ui.util.materialIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onBack: () -> Unit = {},
    viewModel: CategoryManagementViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable { editing = category }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(category.color)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = materialIcon(category.icon),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(category.name, style = MaterialTheme.typography.bodyLarge)
                        if (category.isDefault) {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "Default",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (!category.isDefault) {
                        IconButton(onClick = { deleting = category }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete ${category.name}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        CategoryDialog(
            existing = null,
            allCategories = categories,
            onSave = { name, icon, color ->
                viewModel.addCategory(name, icon, color)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    editing?.let { category ->
        CategoryDialog(
            existing = category,
            allCategories = categories,
            onSave = { name, icon, color ->
                viewModel.updateCategory(category.copy(name = name, icon = icon, color = color))
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { category ->
        CategoryDeleteDialog(
            category = category,
            onConfirm = {
                viewModel.deleteCategory(category)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}
```

Add the missing imports this file needs: `androidx.compose.foundation.background`, `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.PaddingValues`.

- [ ] **Step 3: Build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. If compose imports are wrong the build fails — fix them.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryManagementScreen.kt app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryDialog.kt app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryDeleteDialog.kt
git commit -m "feat: add category management screen with add/edit/delete dialogs"
```

---

### Task 5: Wire navigation + Settings row

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `CategoryManagementScreen(onBack)` (Task 4).
- Produces: route `"categories"`; `SettingsScreen(onNavigateToCategories: () -> Unit = {})` param. No later tasks depend on these.

- [ ] **Step 1: Add the route**

Open `app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt`. Add import `com.smsexpensetracker.ui.screens.categories.CategoryManagementScreen`. Change the Settings composable line:

```kotlin
        composable(BottomNavItem.Settings.route) {
            SettingsScreen(
                onNavigateToCategories = { navController.navigate("categories") }
            )
        }
        composable("categories") {
            CategoryManagementScreen(onBack = { navController.popBackStack() })
        }
```

- [ ] **Step 2: Add the Settings row**

Open `app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt`. Change the function signature:

```kotlin
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToCategories: () -> Unit = {}
) {
```

Insert a "Categories" section between the Appearance block (which ends at line ~87 `Spacer(modifier = Modifier.size(32.dp))`) and the "About" section. Replace that single `Spacer` with:

```kotlin
        Spacer(modifier = Modifier.size(32.dp))

        Text(
            text = "Data",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.size(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToCategories)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Categories",
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

        Spacer(modifier = Modifier.size(32.dp))
```

Add imports: `androidx.compose.material.icons.filled.Category`, `androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight`. `Icons.Filled.Category` and the KeyboardArrowRight icon are both in material-icons-core (verified).

- [ ] **Step 3: Build + full gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass (176 — 165 existing + 6 validation + 5 viewmodel).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/navigation/NavGraph.kt app/src/main/java/com/smsexpensetracker/ui/screens/settings/SettingsScreen.kt
git commit -m "feat: wire Settings row and navigation route for category management"
```

---

### Task 6: Update TODO.md

**Files:**
- Modify: `TODO.md`

- [ ] **Step 1: Mark Settings sub-feature progress**

In `TODO.md`, Task 14 section, change:

```markdown
- [ ] Implement category management: list, add, edit, delete
```

to:

```markdown
- [x] Implement category management: list, add, edit, delete
```

Leave the rest of Task 14 unchecked.

- [ ] **Step 2: Commit**

```bash
git add TODO.md
git commit -m "docs: mark category management complete in TODO"
```

---

## Self-Review

**1. Spec coverage:**
- Repository write methods (spec §5) → Task 1.
- Icon mapping + color palette + validation helper (spec §9, §10, §11) → Task 2.
- ViewModel with seeded-delete guard (spec §6, §11) → Task 3.
- Screen, dialog, delete-confirm (spec §7) → Task 4.
- Navigation route + Settings row (spec §8) → Task 5.
- TODO.md update (spec §12) → Task 6.
- Verification (spec §13) → gate after every task.

**2. Placeholder scan:** No TBD/TODO/incomplete steps; all code blocks are complete implementations.

**3. Type consistency:** `materialIcon(name): ImageVector`, `CATEGORY_COLORS: List<Int>`, `CATEGORY_ICON_NAMES: List<String>`, `validateCategoryName(name, existing: List<Category>, editingId: Long?): String?` defined in Task 2 and used identically in Tasks 4. `CategoryManagementViewModel.categories/addCategory/updateCategory/deleteCategory` defined in Task 3, used in Task 4. `CategoryRepository.insert/update/delete` defined in Task 1, used in Task 3. Route `"categories"` and `onNavigateToCategories` defined in Task 5 only.
