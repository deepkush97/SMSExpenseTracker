package com.smsexpensetracker.domain.value

data class SyncRange(
    val startTimestamp: Long,
    val endTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private val now: Long get() = System.currentTimeMillis()
        val LAST_1D = SyncRange(now - 86_400_000L)
        val LAST_1W = SyncRange(now - 604_800_000L)
        val LAST_2W = SyncRange(now - 1_209_600_000L)
        val LAST_1M = SyncRange(now - 2_592_000_000L)
        val LAST_3M = SyncRange(now - 7_776_000_000L)
        val ALL = SyncRange(0L)
    }
}
