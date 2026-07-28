package com.smsexpensetracker.domain.repository

import com.smsexpensetracker.domain.model.ParseLog
import kotlinx.coroutines.flow.Flow

interface ParseLogRepository {
    fun getAllLogs(): Flow<List<ParseLog>>
    suspend fun insert(log: ParseLog)
}