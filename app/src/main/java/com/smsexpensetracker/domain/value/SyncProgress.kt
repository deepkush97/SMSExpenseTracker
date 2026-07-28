package com.smsexpensetracker.domain.value

enum class SyncStatus { IDLE, RUNNING, COMPLETED, FAILED }

data class SyncProgress(
    val progress: Int = 0,
    val total: Int = 0,
    val status: SyncStatus = SyncStatus.IDLE
)