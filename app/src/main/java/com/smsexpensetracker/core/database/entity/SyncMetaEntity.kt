package com.smsexpensetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 1,
    val lastSyncTimeStamp: Long = 0,
    val lastSmsId: String? = null
)