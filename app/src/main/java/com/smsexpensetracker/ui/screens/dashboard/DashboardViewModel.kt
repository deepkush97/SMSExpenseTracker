package com.smsexpensetracker.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.BankSummary
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.CategorySummary
import com.smsexpensetracker.domain.model.MonthlySummary
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
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
    val categories: List<Category> = emptyList(),
    val banks: List<Bank> = emptyList(),
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

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<DashboardUiState> = combine(
        getDashboardDataUseCase().totalSpent,
        getDashboardDataUseCase().totalReceived,
        getDashboardDataUseCase().bankSummary,
        getDashboardDataUseCase().monthlySummary,
        getDashboardDataUseCase().categorySummary,
        getDashboardDataUseCase().recentTransactions,
        bankRepository.getAllBanks(),
        categoryRepository.getAllCategories()
    ) { array ->
        val spent = array[0] as? Long
        val received = array[1] as? Long
        val bankSums = array[2] as? List<BankSummary> ?: emptyList()
        val monthlySums = array[3] as? List<MonthlySummary> ?: emptyList()
        val catSums = array[4] as? List<CategorySummary> ?: emptyList()
        val recent = array[5] as? List<Transaction> ?: emptyList()
        val banks = array[6] as? List<Bank> ?: emptyList()
        val cats = array[7] as? List<Category> ?: emptyList()
        val bankMap = banks.associateBy { it.id }
        val catMap = cats.associateBy { it.id }
        DashboardUiState(
            totalSpent = spent ?: 0,
            totalReceived = received ?: 0,
            bankChartData = bankSums.toBankBarItems(bankMap),
            monthlyChartData = monthlySums.toMonthlyLineItems(),
            categoryChartData = catSums.toCategoryPieItems(catMap),
            recentTransactions = recent,
            categories = cats,
            banks = banks,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private fun List<BankSummary>.toBankBarItems(
        bankMap: Map<Long, Bank>
    ): List<BankBarItem> {
        val byBank = groupBy { it.bankId }
        return byBank.map { (bankId, summaries) ->
            BankBarItem(
                bankName = bankMap[bankId]?.name ?: "Bank $bankId",
                credit = summaries.find { it.type == TransactionType.CREDIT }?.total ?: 0,
                debit = summaries.find { it.type == TransactionType.DEBIT }?.total ?: 0
            )
        }
    }

    private fun List<MonthlySummary>.toMonthlyLineItems(): List<MonthlyLineItem> {
        val byMonth = groupBy { it.yearMonth }
        return byMonth.map { (month, summaries) ->
            MonthlyLineItem(
                month = month,
                credit = summaries.find { it.type == TransactionType.CREDIT }?.total ?: 0,
                debit = summaries.find { it.type == TransactionType.DEBIT }?.total ?: 0
            )
        }.sortedBy { it.month }
    }

    private fun List<CategorySummary>.toCategoryPieItems(
        catMap: Map<Long, Category>
    ): List<CategoryPieItem> {
        return map { cs ->
            CategoryPieItem(
                categoryName = cs.categoryId?.let { catMap[it]?.name } ?: "Uncategorized",
                color = cs.categoryId?.let { catMap[it]?.color } ?: 0xFF6B7280.toInt(),
                amount = cs.total
            )
        }.sortedByDescending { it.amount }
    }
}
