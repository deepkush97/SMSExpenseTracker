package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.database.entity.TransactionEntity
import com.smsexpensetracker.domain.model.BankSummary
import com.smsexpensetracker.domain.model.CategorySummary
import com.smsexpensetracker.domain.model.MonthlySummary
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
) : TransactionRepository {
    override fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions().mapToDomain()

    override fun getTransactionsByBank(bankId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByBank(bankId).mapToDomain()

    override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByCategory(categoryId).mapToDomain()

    override fun searchTransactions(query: String): Flow<List<Transaction>> =
        transactionDao.searchTransactions(query).mapToDomain()

    override fun getTransactionsBetweenDates(
        start: Long,
        end: Long
    ): Flow<List<Transaction>> =
        transactionDao.getTransactionsBetweenDates(start, end).mapToDomain()

    override suspend fun getTransactionById(id: Long): Transaction? =
        transactionDao.getTransactionById(id)?.toDomain()


    override suspend fun insert(transaction: Transaction): Long =
        transactionDao.insert(transaction.toEntity())

    override suspend fun delete(transaction: Transaction) =
        transactionDao.delete(transaction.toEntity())

    override suspend fun updateTransactionCategory(id: Long, categoryId: Long?) {
        transactionDao.updateTransactionCategory(id, categoryId)
    }

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

    private fun Flow<List<TransactionEntity>>.mapToDomain(): Flow<List<Transaction>> =
        map { list -> list.map { it.toDomain() } }

    private fun Transaction.toEntity() = TransactionEntity(
        id,
        bankId,
        amount,
        type = com.smsexpensetracker.core.database.entity.TransactionType.valueOf(transactionType.name),
        description,
        transactionDate,
        categoryId,
        rawSms,
        smsTimestamp,
        createdAt,
    )

    private fun TransactionEntity.toDomain() = Transaction(
        id,
        bankId,
        amount,
        transactionType = com.smsexpensetracker.domain.model.TransactionType.valueOf(type.name),
        description,
        transactionDate,
        categoryId,
        rawSms,
        smsTimestamp,
        createdAt,
    )
}