package com.smsexpensetracker.domain.usecase

import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

class GetTransactionsUseCase(private val repository: TransactionRepository) {
    operator fun invoke(): Flow<List<Transaction>> {
        return repository.getAllTransactions()
    }
}