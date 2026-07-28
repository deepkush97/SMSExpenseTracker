package com.smsexpensetracker.domain.model

data class SyncMeta(
    val lastSyncTimestamp: Long,
    val lastSmsId: String?
)