package com.smsexpensetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "parse_logs")
data class ParseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val smsBody: String,
    val smsSender: String,
    val parsedAt: LocalDateTime = LocalDateTime.now(),
    val status: ParseStatus,
    val errorMessage: String? = null,

    )

enum class ParseStatus {
    SUCCESS, FAILED, SKIPPED
}