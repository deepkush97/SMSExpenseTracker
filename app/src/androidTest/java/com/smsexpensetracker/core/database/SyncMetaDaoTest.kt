package com.smsexpensetracker.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.core.database.dao.SyncMetaDao
import com.smsexpensetracker.core.database.entity.SyncMetaEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncMetaDaoTest {

    private lateinit var db: SmsExpenseDatabase
    private lateinit var syncMetaDao: SyncMetaDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmsExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncMetaDao = db.syncMetaDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertThenGetReturnsSameRow() = runTest {
        db.syncMetaDao().upsert(SyncMetaEntity(id = 1, lastSyncTimeStamp = 111L, lastSmsId = null))
        assertEquals(111L, db.syncMetaDao().get()!!.lastSyncTimeStamp)

        db.syncMetaDao().upsert(SyncMetaEntity(id = 1, lastSyncTimeStamp = 222L, lastSmsId = "99"))
        assertEquals(222L, db.syncMetaDao().get()!!.lastSyncTimeStamp)
        assertEquals("99", db.syncMetaDao().get()!!.lastSmsId)
    }
}
