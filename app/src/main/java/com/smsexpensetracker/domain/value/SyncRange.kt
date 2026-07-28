package com.smsexpensetracker.domain.value

data class SyncRange(
    val startTimestamp: Long,
    val endTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        val LAST_1D = SyncRange(System.currentTimeMillis() - 86_400_000L)
        val LAST_1W = SyncRange(System.currentTimeMillis() - 604_800_000L)
        val LAST_1M = SyncRange(System.currentTimeMillis() - 2_592_000_000L)
        val LAST_3M = SyncRange(System.currentTimeMillis() - 7_776_000_000L)
        val ALL = SyncRange(0L)
    }
}