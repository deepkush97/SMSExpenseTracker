package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.settings.DemoDataPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoDataSeeder @Inject constructor(
    private val transactionDao: TransactionDao,
    private val demoDataPreferences: DemoDataPreferences
) {
    suspend fun seedIfEmpty(): Int {
        if (transactionDao.count() == 0) {
            val transactions = DemoTransactionGenerator.generate()
            transactionDao.insertAll(transactions)
            demoDataPreferences.setDemoDataLoaded(true)
            return transactions.size
        }
        return 0
    }

    suspend fun deleteDemoData() {
        transactionDao.deleteAll()
        demoDataPreferences.setDemoDataLoaded(false)
    }
}
