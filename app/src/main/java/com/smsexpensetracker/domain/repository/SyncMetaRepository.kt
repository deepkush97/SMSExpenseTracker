package com.smsexpensetracker.domain.repository

import com.smsexpensetracker.domain.model.SyncMeta

interface SyncMetaRepository {
    suspend fun get(): SyncMeta?
    suspend fun upsert(meta: SyncMeta)
}