package com.smsexpensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smsexpensetracker.core.database.entity.SyncMetaEntity

@Dao
interface SyncMetaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: SyncMetaEntity)

    @Query("SELECT * FROM sync_meta where id=1")
    suspend fun get(): SyncMetaEntity?

}