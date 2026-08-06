package com.smsexpensetracker.domain.value

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRangeTest {

    @Test
    fun `presets cover expected durations`() {
        val now = System.currentTimeMillis()
        assertTrue(now - SyncRange.LAST_1D.startTimestamp <= 86_400_000L + 1_000L)
        assertTrue(now - SyncRange.LAST_1W.startTimestamp <= 604_800_000L + 1_000L)
        assertTrue(now - SyncRange.LAST_2W.startTimestamp <= 1_209_600_000L + 1_000L)
        assertTrue(now - SyncRange.LAST_1M.startTimestamp <= 2_592_000_000L + 1_000L)
        assertTrue(now - SyncRange.LAST_3M.startTimestamp <= 7_776_000_000L + 1_000L)
        assertEquals(0L, SyncRange.ALL.startTimestamp)
    }
}
