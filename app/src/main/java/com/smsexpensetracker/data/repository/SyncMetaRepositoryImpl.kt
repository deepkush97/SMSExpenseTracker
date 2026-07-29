package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.SyncMetaDao
import com.smsexpensetracker.core.database.entity.SyncMetaEntity
import com.smsexpensetracker.domain.model.SyncMeta
import com.smsexpensetracker.domain.repository.SyncMetaRepository
import javax.inject.Inject

class SyncMetaRepositoryImpl @Inject constructor(
    private val syncMetaDao: SyncMetaDao
) : SyncMetaRepository {
    override suspend fun get(): SyncMeta? = syncMetaDao.get()?.toDomain()

    override suspend fun upsert(meta: SyncMeta) =
        syncMetaDao.upsert(meta.toEntity())

    private fun SyncMeta.toEntity() =
        SyncMetaEntity(id = 1, lastSyncTimestamp, lastSmsId)

    private fun SyncMetaEntity.toDomain() =
        SyncMeta(lastSyncTimeStamp, lastSmsId)
}