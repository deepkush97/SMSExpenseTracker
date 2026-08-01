package com.smsexpensetracker.domain.value

data class SyncResult(
    val scanned: Int = 0,
    val inserted: Int = 0,
    val unparsed: Int = 0,
    val error: String? = null
)
