package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.ParseLogDao
import com.smsexpensetracker.core.database.entity.ParseLogEntity
import com.smsexpensetracker.domain.model.ParseLog

import com.smsexpensetracker.domain.repository.ParseLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ParseLogRepositoryImpl @Inject constructor(
    private val parseLogDao: ParseLogDao
) : ParseLogRepository {
    override fun getAllLogs(): Flow<List<ParseLog>> =
        parseLogDao.getAllLogs().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun insert(log: ParseLog) = parseLogDao.insert(log.toEntity())
    
    private fun ParseLogEntity.toDomain() = ParseLog(
        id,
        smsBody,
        smsSender,
        parsedAt,
        status = com.smsexpensetracker.domain.model.ParseStatus.valueOf(status.name),
        errorMessage
    )

    private fun ParseLog.toEntity() = ParseLogEntity(
        id,
        smsBody,
        smsSender,
        parsedAt,
        status = com.smsexpensetracker.core.database.entity.ParseStatus.valueOf(status.name),
        errorMessage
    )
}