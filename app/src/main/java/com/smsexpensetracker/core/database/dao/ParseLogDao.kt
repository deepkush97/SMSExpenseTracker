package com.smsexpensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.smsexpensetracker.core.database.entity.ParseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParseLogDao {

    @Insert
    suspend fun insert(log: ParseLogEntity)

    @Query("SELECT * FROM parse_logs ORDER BY parsedAt DESC")
    fun getAllLogs(): Flow<List<ParseLogEntity>>

    @Query("DELETE FROM parse_logs where parsedAt < :cutoff")
    suspend fun deleteOldLogs(cutoff: Long)

}