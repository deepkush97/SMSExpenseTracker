package com.smsexpensetracker.domain.usecase

import com.smsexpensetracker.domain.model.BankSummary
import com.smsexpensetracker.domain.model.CategorySummary
import com.smsexpensetracker.domain.model.MonthlySummary
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
