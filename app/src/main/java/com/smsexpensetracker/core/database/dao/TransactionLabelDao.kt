package com.smsexpensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.smsexpensetracker.core.database.entity.TransactionLabelEntity

@Dao
interface TransactionLabelDao {
    @Insert
    suspend fun insert(label: TransactionLabelEntity): Long

    @Query("SELECT * FROM transaction_labels WHERE transactionId = :transactionId")
    fun getAllForTransaction(transactionId: Long): kotlinx.coroutines.flow.Flow<List<TransactionLabelEntity>>

    @Query("DELETE FROM transaction_labels WHERE transactionId = :transactionId")
    suspend fun deleteForTransaction(transactionId: Long)
}
