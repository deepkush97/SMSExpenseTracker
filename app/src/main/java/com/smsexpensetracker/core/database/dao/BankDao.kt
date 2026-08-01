package com.smsexpensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smsexpensetracker.core.database.entity.BankEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bank: BankEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(banks: List<BankEntity>)

    @Update
    suspend fun update(bank: BankEntity)

    @Delete
    suspend fun delete(bank: BankEntity)

    @Query("SELECT * FROM banks ORDER BY name ASC")
    fun getAllBanks(): Flow<List<BankEntity>>

    @Query("SELECT * FROM banks WHERE id = :id")
    suspend fun getBankById(id: Long): BankEntity?

    @Query("SELECT * FROM banks WHERE smsSender = :sender")
    suspend fun getBankBySmsSender(sender: String): BankEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE bankId = :bankId")
    suspend fun getTransactionCount(bankId: Long): Int
}