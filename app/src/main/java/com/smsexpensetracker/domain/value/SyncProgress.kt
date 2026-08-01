package com.smsexpensetracker.domain.value

data class SyncProgress(
    val processed: Int = 0,
    val total: Int = 0,
    val unparsed: Int = 0
)
