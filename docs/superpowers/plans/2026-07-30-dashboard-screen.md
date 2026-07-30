# Dashboard Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder Dashboard with summary cards (spent/received/net), per-bank bar chart, monthly credit/debit line chart, category donut, and recent transactions list.

**Architecture:** 5 new SQL aggregation queries on TransactionDao → TransactionRepository gets 5 new methods → GetDashboardDataUseCase orchestrates flows → DashboardViewModel combines 8 flows (6 from use case + 2 for bank/category names) → DashboardScreen renders scrollable layout.

**Tech Stack:** Room (SQLite aggregations), Kotlin Coroutines Flow, Jetpack Compose, Vico 3.2.3 (compose-m3)

## Global Constraints

- All amounts are in paisa (`Long`). Display: divide by 100 for rupee format with 2 decimals.
- `transactionDate` is epoch-seconds INTEGER — use `strftime('%Y-%m', transactionDate, 'unixepoch')`
- `type` column stores `CREDIT`/`DEBIT` as TEXT
- Vico API is at version 3.2.3 — `compose-m3` module for Material 3 styling
- Follow existing patterns: `Flow<>` for reactive queries, `map` for entity→domain mapping

---
### Task 1: DAO Result Data Classes

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionSummary.kt`

- [ ] **Step 1: Create TransactionSummary.kt**

```kotlin
package com.smsexpensetracker.core.database.dao

import com.smsexpensetracker.core.database.entity.TransactionType

data class BankSummary(val bankId: Long, val type: TransactionType, val total: Long)
data class MonthlySummary(val yearMonth: String, val type: TransactionType, val total: Long)
data class CategorySummary(val categoryId: Long?, val total: Long)
```

- [ ] **Step 2: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionSummary.kt
git commit -m "feat(db): add DAO result data classes for dashboard aggregations"
```

---
### Task 2: Add Aggregation Queries to TransactionDao

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt`

- [ ] **Step 1: Add 5 query methods after existing methods**

```kotlin
@Query("SELECT bankId, type, SUM(amount) AS total FROM transactions GROUP BY bankId, type")
fun getBankSummary(): Flow<List<BankSummary>>

@Query("SELECT strftime('%Y-%m', transactionDate, 'unixepoch') AS yearMonth, type, SUM(amount) AS total FROM transactions GROUP BY yearMonth, type ORDER BY yearMonth")
fun getMonthlySummary(): Flow<List<MonthlySummary>>

@Query("SELECT categoryId, SUM(amount) AS total FROM transactions WHERE type = 'DEBIT' GROUP BY categoryId")
fun getCategorySummary(): Flow<List<CategorySummary>>

@Query("SELECT SUM(amount) FROM transactions WHERE type = :type")
fun getTotalByType(type: TransactionType): Flow<Long?>

@Query("SELECT * FROM transactions ORDER BY transactionDate DESC LIMIT 5")
fun getRecentTransactions(): Flow<List<TransactionEntity>>
```

Do NOT import `BankSummary`/`MonthlySummary`/`CategorySummary` — they're in the same package.

- [ ] **Step 2: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/core/database/dao/TransactionDao.kt
git commit -m "feat(db): add aggregation queries for dashboard charts"
```

---
### Task 3: Domain Models for Aggregations

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/domain/model/BankSummary.kt`
- Create: `app/src/main/java/com/smsexpensetracker/domain/model/MonthlySummary.kt`
- Create: `app/src/main/java/com/smsexpensetracker/domain/model/CategorySummary.kt`

- [ ] **Step 1: Create BankSummary.kt**

```kotlin
package com.smsexpensetracker.domain.model

data class BankSummary(val bankId: Long, val type: TransactionType, val total: Long)
```

- [ ] **Step 2: Create MonthlySummary.kt**

```kotlin
package com.smsexpensetracker.domain.model

data class MonthlySummary(val yearMonth: String, val type: TransactionType, val total: Long)
```

- [ ] **Step 3: Create CategorySummary.kt**

```kotlin
package com.smsexpensetracker.domain.model

data class CategorySummary(val categoryId: Long?, val total: Long)
```

- [ ] **Step 4: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/model/BankSummary.kt app/src/main/java/com/smsexpensetracker/domain/model/MonthlySummary.kt app/src/main/java/com/smsexpensetracker/domain/model/CategorySummary.kt
git commit -m "feat(domain): add aggregation model classes for dashboard"
```

---
### Task 4: Add Methods to TransactionRepository + Impl

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt`

- [ ] **Step 1: Add 5 methods to TransactionRepository interface**

Add after the last existing method, before the closing `}`:

```kotlin
fun getBankSummary(): Flow<List<BankSummary>>
fun getMonthlySummary(): Flow<List<MonthlySummary>>
fun getCategorySummary(): Flow<List<CategorySummary>>
fun getTotalByType(type: TransactionType): Flow<Long?>
fun getRecentTransactions(): Flow<List<Transaction>>
```

Add imports:
```kotlin
import com.smsexpensetracker.domain.model.BankSummary
import com.smsexpensetracker.domain.model.MonthlySummary
import com.smsexpensetracker.domain.model.CategorySummary
```

- [ ] **Step 2: Implement in TransactionRepositoryImpl**

Add after `delete(...)` method, before the private helpers:

```kotlin
override fun getBankSummary(): Flow<List<BankSummary>> =
    transactionDao.getBankSummary().map { list ->
        list.map { BankSummary(it.bankId, TransactionType.valueOf(it.type.name), it.total) }
    }

override fun getMonthlySummary(): Flow<List<MonthlySummary>> =
    transactionDao.getMonthlySummary().map { list ->
        list.map { MonthlySummary(it.yearMonth, TransactionType.valueOf(it.type.name), it.total) }
    }

override fun getCategorySummary(): Flow<List<CategorySummary>> =
    transactionDao.getCategorySummary().map { list ->
        list.map { CategorySummary(it.categoryId, it.total) }
    }

override fun getTotalByType(type: TransactionType): Flow<Long?> =
    transactionDao.getTotalByType(com.smsexpensetracker.core.database.entity.TransactionType.valueOf(type.name))

override fun getRecentTransactions(): Flow<List<Transaction>> =
    transactionDao.getRecentTransactions().map { list -> list.map { it.toDomain() } }
```

Add imports to impl:
```kotlin
import com.smsexpensetracker.domain.model.BankSummary
import com.smsexpensetracker.domain.model.MonthlySummary
import com.smsexpensetracker.domain.model.CategorySummary
import com.smsexpensetracker.domain.model.TransactionType
```

- [ ] **Step 3: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/repository/TransactionRepository.kt app/src/main/java/com/smsexpensetracker/data/repository/TransactionRepositoryImpl.kt
git commit -m "feat(data): add dashboard aggregation methods to repository"
```

---
### Task 5: Create GetDashboardDataUseCase

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/domain/usecase/GetDashboardDataUseCase.kt`

- [ ] **Step 1: Create GetDashboardDataUseCase.kt**

```kotlin
package com.smsexpensetracker.domain.usecase

import com.smsexpensetracker.domain.model.CategorySummary
import com.smsexpensetracker.domain.model.MonthlySummary
import com.smsexpensetracker.domain.model.BankSummary
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDashboardDataUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    operator fun invoke(): DashboardData = DashboardData(
        totalSpent = repository.getTotalByType(TransactionType.DEBIT),
        totalReceived = repository.getTotalByType(TransactionType.CREDIT),
        bankSummary = repository.getBankSummary(),
        monthlySummary = repository.getMonthlySummary(),
        categorySummary = repository.getCategorySummary(),
        recentTransactions = repository.getRecentTransactions()
    )

    data class DashboardData(
        val totalSpent: Flow<Long?>,
        val totalReceived: Flow<Long?>,
        val bankSummary: Flow<List<BankSummary>>,
        val monthlySummary: Flow<List<MonthlySummary>>,
        val categorySummary: Flow<List<CategorySummary>>,
        val recentTransactions: Flow<List<Transaction>>
    )
}
```

- [ ] **Step 2: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/domain/usecase/GetDashboardDataUseCase.kt
git commit -m "feat(domain): add GetDashboardDataUseCase with all dashboard flows"
```

---
### Task 6: Implement DashboardViewModel

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardViewModel.kt`

- [ ] **Step 1: Replace DashboardViewModel.kt**

```kotlin
package com.smsexpensetracker.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.usecase.GetDashboardDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val totalSpent: Long = 0,
    val totalReceived: Long = 0,
    val bankChartData: List<BankBarItem> = emptyList(),
    val monthlyChartData: List<MonthlyLineItem> = emptyList(),
    val categoryChartData: List<CategoryPieItem> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = true
)

data class BankBarItem(val bankName: String, val credit: Long, val debit: Long)
data class MonthlyLineItem(val month: String, val credit: Long, val debit: Long)
data class CategoryPieItem(val categoryName: String, val color: Int, val amount: Long)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardDataUseCase: GetDashboardDataUseCase,
    private val bankRepository: BankRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        getDashboardDataUseCase().totalSpent,
        getDashboardDataUseCase().totalReceived,
        getDashboardDataUseCase().bankSummary,
        getDashboardDataUseCase().monthlySummary,
        getDashboardDataUseCase().categorySummary,
        getDashboardDataUseCase().recentTransactions,
        bankRepository.getAllBanks(),
        categoryRepository.getAllCategories()
    ) { spent, received, bankSums, monthlySums, catSums, recent, banks, cats ->
        val bankMap = banks.associateBy { it.id }
        val catMap = cats.associateBy { it.id }
        DashboardUiState(
            totalSpent = spent ?: 0,
            totalReceived = received ?: 0,
            bankChartData = bankSums.toBankBarItems(bankMap),
            monthlyChartData = monthlySums.toMonthlyLineItems(),
            categoryChartData = catSums.toCategoryPieItems(catMap),
            recentTransactions = recent,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private fun List<com.smsexpensetracker.domain.model.BankSummary>.toBankBarItems(
        bankMap: Map<Long, Bank>
    ): List<BankBarItem> {
        val byBank = groupBy { it.bankId }
        return byBank.map { (bankId, summaries) ->
            BankBarItem(
                bankName = bankMap[bankId]?.name ?: "Bank $bankId",
                credit = summaries.find { it.type == com.smsexpensetracker.domain.model.TransactionType.CREDIT }?.total ?: 0,
                debit = summaries.find { it.type == com.smsexpensetracker.domain.model.TransactionType.DEBIT }?.total ?: 0
            )
        }
    }

    private fun List<com.smsexpensetracker.domain.model.MonthlySummary>.toMonthlyLineItems(): List<MonthlyLineItem> {
        val byMonth = groupBy { it.yearMonth }
        return byMonth.map { (month, summaries) ->
            MonthlyLineItem(
                month = month,
                credit = summaries.find { it.type == com.smsexpensetracker.domain.model.TransactionType.CREDIT }?.total ?: 0,
                debit = summaries.find { it.type == com.smsexpensetracker.domain.model.TransactionType.DEBIT }?.total ?: 0
            )
        }.sortedBy { it.month }
    }

    private fun List<com.smsexpensetracker.domain.model.CategorySummary>.toCategoryPieItems(
        catMap: Map<Long, Category>
    ): List<CategoryPieItem> {
        return map { cs ->
            CategoryPieItem(
                categoryName = cs.categoryId?.let { catMap[it]?.name } ?: "Uncategorized",
                color = cs.categoryId?.let { catMap[it]?.color } ?: 0xFF6B7280.toInt(),
                amount = cs.total
            )
        }
    }
}
```

- [ ] **Step 2: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardViewModel.kt
git commit -m "feat(viewmodel): implement DashboardViewModel with combined flows"
```

---
### Task 7: Create SummaryCard Composable

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/SummaryCard.kt`

- [ ] **Step 1: Create SummaryCard.kt**

```kotlin
package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SummaryCard(
    label: String,
    amountPaisa: Long,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedAmount by animateIntAsState(
        targetValue = amountPaisa.toInt(),
        animationSpec = tween(durationMillis = 600)
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatPaisa(animatedAmount.toLong()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

fun formatPaisa(paisa: Long): String {
    val rupees = paisa / 100
    val paise = (paisa % 100).absoluteValue
    val sign = if (paisa < 0) "-" else ""
    return "₹$sign${rupees}.${paise.toString().padStart(2, '0')}"
}
```

- [ ] **Step 2: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/SummaryCard.kt
git commit -m "feat(ui): add SummaryCard with animated counter and paisa formatting"
```

---
### Task 8: Create BankChart Composable

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/BankChart.kt`

- [ ] **Step 1: Create BankChart.kt**

```kotlin
package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.common.component.TextComponent

@Composable
fun BankChart(
    data: List<BankBarItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Spending by Bank",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (data.isEmpty()) {
            Text(
                text = "No transaction data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val modelProducer = remember { CartesianChartModelProducer() }
            val creditSeries = data.map { it.credit / 100.0 }
            val debitSeries = data.map { it.debit / 100.0 }
            val labels = data.map { it.bankName }

            modelProducer.runTransaction {
                columnSeries { series(creditSeries, debitSeries) }
            }

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer()
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ -> labels.getOrNull(value.toInt()) ?: "" }
                )
            )
        }
    }
}
```

Note: The Vico API used here is representative of 3.x but may need adjustment — verify against actual Vico 3.2.3 API if compile fails.

- [ ] **Step 2: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/BankChart.kt
git commit -m "feat(ui): add per-bank bar chart composable"
```

---
### Task 9: Create MonthlyChart Composable

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/MonthlyChart.kt`

- [ ] **Step 1: Create MonthlyChart.kt**

```kotlin
package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

@Composable
fun MonthlyChart(
    data: List<MonthlyLineItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Monthly Trend",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (data.isEmpty()) {
            Text(
                text = "No monthly data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val modelProducer = remember { CartesianChartModelProducer() }
            val creditSeries = data.map { it.credit / 100.0 }
            val debitSeries = data.map { it.debit / 100.0 }

            modelProducer.runTransaction {
                lineSeries { series(creditSeries, debitSeries) }
            }

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer()
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis()
            )
        }
    }
}
```

- [ ] **Step 2: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/MonthlyChart.kt
git commit -m "feat(ui): add monthly credit/debit line chart composable"
```

---
### Task 10: Create CategoryChart Composable

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/CategoryChart.kt`

- [ ] **Step 1: Create CategoryChart.kt**

```kotlin
package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.chart.pie.rememberPieChart
import com.patrykandpatrick.vico.compose.chart.pie.PieChartHost
import com.patrykandpatrick.vico.core.chart.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.core.chart.pie.data.pieSlices

@Composable
fun CategoryChart(
    data: List<CategoryPieItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Category Breakdown",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (data.isEmpty()) {
            Text(
                text = "No categorized spending yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val modelProducer = remember { PieChartModelProducer() }
            val values = data.map { it.amount / 100.0 }
            val colors = data.map { Color(it.color) }

            modelProducer.runTransaction {
                pieSlices { slices(values) }
            }

            PieChartHost(
                pieChart = rememberPieChart(),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/CategoryChart.kt
git commit -m "feat(ui): add category donut chart composable"
```

---
### Task 11: Create TransactionRow Composable

**Files:**
- Create: `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/TransactionRow.kt`

- [ ] **Step 1: Create TransactionRow.kt**

```kotlin
package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.ui.theme.Green40
import com.smsexpensetracker.ui.theme.Red40
import java.time.format.DateTimeFormatter

@Composable
fun TransactionRow(
    transaction: Transaction,
    categoryColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (categoryColor != null) {
                Surface(
                    modifier = Modifier.size(12.dp).clip(CircleShape),
                    color = categoryColor
                ) {}
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = transaction.transactionDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = formatPaisa(transaction.amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (transaction.transactionType == TransactionType.CREDIT) Green40 else Red40
        )
    }
}
```

- [ ] **Step 2: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/TransactionRow.kt
git commit -m "feat(ui): add transaction row composable for recent list"
```

---
### Task 12: Assemble DashboardScreen

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Replace DashboardScreen.kt**

```kotlin
package com.smsexpensetracker.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsexpensetracker.ui.theme.Blue40
import com.smsexpensetracker.ui.theme.Green40
import com.smsexpensetracker.ui.theme.Red40

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isLoading && state.recentTransactions.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Summary cards row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                label = "Spent",
                amountPaisa = state.totalSpent,
                icon = Icons.Default.ArrowDownward,
                color = Red40,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                label = "Received",
                amountPaisa = state.totalReceived,
                icon = Icons.Default.ArrowUpward,
                color = Green40,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                label = "Net",
                amountPaisa = state.totalReceived - state.totalSpent,
                icon = Icons.Default.SwapHoriz,
                color = if (state.totalReceived >= state.totalSpent) Blue40 else Red40,
                modifier = Modifier.weight(1f)
            )
        }

        // Charts
        BankChart(data = state.bankChartData)
        Divider()
        MonthlyChart(data = state.monthlyChartData)
        Divider()
        CategoryChart(data = state.categoryChartData)

        // Recent transactions header
        Text(
            text = "Recent Transactions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
        )

        if (state.recentTransactions.isEmpty()) {
            Text(
                text = "No transactions yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            state.recentTransactions.forEach { tx ->
                TransactionRow(
                    transaction = tx,
                    categoryColor = null, // Color lookup can be added later
                    onClick = { /* TODO: open detail sheet in Task 11 */ }
                )
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
```

- [ ] **Step 2: Full build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run tests to make sure nothing broke**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL (existing tests still pass)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardScreen.kt
git commit -m "feat(ui): assemble DashboardScreen with summary cards, charts, and recent list"
```

---
### Task 13: Verify Complete Build

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Update TODO.md**

Mark Task 10 sections as `[x]` or `[-]` (in progress) as appropriate.
