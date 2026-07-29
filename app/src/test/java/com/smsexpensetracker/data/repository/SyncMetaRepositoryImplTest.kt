package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.SyncMetaDao
import com.smsexpensetracker.core.database.entity.SyncMetaEntity
import com.smsexpensetracker.domain.model.SyncMeta
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class SyncMetaRepositoryImplTest {
    private val syncMetaDao = mockk<SyncMetaDao>()
    private lateinit var repo: SyncMetaRepositoryImpl

    @Before
    fun setup() {
        repo = SyncMetaRepositoryImpl(syncMetaDao)
    }

    @Test
    fun `get return null if not found`() = runTest {


        coEvery { syncMetaDao.get() } returns null

        val result = repo.get()

        assertEquals(null, result)
    }

    @Test
    fun `get return item if found`() = runTest {
        val entity = SyncMetaEntity(
            1,
            LocalDateTime.of(2026, 6, 15, 10, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            "1L"
        )

        coEvery { syncMetaDao.get() } returns entity

        val result = repo.get()

        assertEquals(entity.lastSyncTimeStamp, result?.lastSyncTimestamp)
        assertEquals(entity.lastSmsId, result?.lastSmsId)
    }


    @Test
    fun `upsert works`() = runTest {
        val entity = SyncMetaEntity(
            1,
            LocalDateTime.of(2026, 6, 15, 10, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            "1L"
        )

        coEvery { syncMetaDao.upsert(any<SyncMetaEntity>()) } coAnswers { Unit }

        repo.upsert(
            SyncMeta(
                entity.lastSyncTimeStamp,
                entity.lastSmsId,
            )
        )

        coVerify { syncMetaDao.upsert(any()) }
    }
}