package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.dao.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoDataSeeder @Inject constructor(
    private val transactionDao: TransactionDao
) {
    suspend fun seedIfEmpty(): Int {
        if (transactionDao.count() == 0) {
            val transactions = DemoTransactionGenerator.generate()
            transactionDao.insertAll(transactions)
            return transactions.size
        }
        return 0
    }
}
