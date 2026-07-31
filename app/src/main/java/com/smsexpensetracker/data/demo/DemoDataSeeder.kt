package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.dao.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoDataSeeder @Inject constructor(
    private val transactionDao: TransactionDao
) {
    suspend fun seedIfEmpty() {
        if (transactionDao.count() == 0) {
            transactionDao.insertAll(DemoTransactionGenerator.generate())
        }
    }
}
