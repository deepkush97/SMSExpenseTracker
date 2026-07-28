package com.smsexpensetracker.domain.repository

import com.smsexpensetracker.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    suspend fun getTransactionById(id: Long): Transaction?
    fun getTransactionsByBank(bankId: Long): Flow<List<Transaction>>
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>
    fun searchTransactions(query: String): Flow<List<Transaction>>
    fun getTransactionsBetweenDates(start: Long, end: Long): Flow<List<Transaction>>
    suspend fun insert(transaction: Transaction): Long
    suspend fun delete(transaction: Transaction)
}