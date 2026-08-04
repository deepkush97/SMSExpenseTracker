package com.smsexpensetracker.domain.repository

import com.smsexpensetracker.domain.model.BankSummary
import com.smsexpensetracker.domain.model.CategorySummary
import com.smsexpensetracker.domain.model.MonthlySummary
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    suspend fun getTransactionById(id: Long): Transaction?
    fun getTransactionsByBank(bankId: Long): Flow<List<Transaction>>
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>
    fun searchTransactions(query: String): Flow<List<Transaction>>
    fun getTransactionsBetweenDates(start: Long, end: Long): Flow<List<Transaction>>
    suspend fun insert(transaction: Transaction): Long
    suspend fun insertBatch(transactions: List<Transaction>): Int
    suspend fun delete(transaction: Transaction)
    suspend fun updateTransactionCategory(id: Long, categoryId: Long?)
    fun getBankSummary(): Flow<List<BankSummary>>
    fun getMonthlySummary(): Flow<List<MonthlySummary>>
    fun getCategorySummary(): Flow<List<CategorySummary>>
    fun getTotalByType(type: TransactionType): Flow<Long?>
    fun getRecentTransactions(): Flow<List<Transaction>>
    suspend fun deleteAll()
}