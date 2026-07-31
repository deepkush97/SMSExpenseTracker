package com.smsexpensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smsexpensetracker.core.database.entity.TransactionEntity
import com.smsexpensetracker.core.database.entity.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE bankId = :bankId ORDER BY transactionDate DESC")
    fun getTransactionsByBank(bankId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY transactionDate DESC")
    fun getTransactionsByCategory(categoryId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE description LIKE '%' || :query || '%' ORDER BY transactionDate DESC")
    fun searchTransactions(query: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transactionDate BETWEEN :start AND :end ORDER BY transactionDate DESC")
    fun getTransactionsBetweenDates(start: Long, end: Long): Flow<List<TransactionEntity>>

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

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateTransactionCategory(id: Long, categoryId: Long?)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}