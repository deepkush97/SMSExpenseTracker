package com.smsexpensetracker.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.data.repository.BankRepositoryImpl
import com.smsexpensetracker.data.repository.CategoryRepositoryImpl
import com.smsexpensetracker.data.repository.ParseLogRepositoryImpl
import com.smsexpensetracker.data.repository.SmsRuleRepositoryImpl
import com.smsexpensetracker.data.repository.SyncMetaRepositoryImpl
import com.smsexpensetracker.data.repository.TransactionLabelRepositoryImpl
import com.smsexpensetracker.data.repository.TransactionRepositoryImpl
import com.smsexpensetracker.data.sms.SmsReader
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(AndroidJUnit4::class)
class SmsSyncDateIntegrationTest {

    private lateinit var db: SmsExpenseDatabase
    private lateinit var useCase: SmsSyncUseCase
    private lateinit var dataStore: DataStore<Preferences>

    private val oldEpochMillis = 1750000000000L // 2025-06-15 00:00:00 UTC

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = null,
            migrations = emptyList(),
            scope = kotlinx.coroutines.CoroutineScope(EmptyCoroutineContext),
            produceFile = { context.preferencesDataStoreFile("sync_date_test") }
        )
        db = Room.inMemoryDatabaseBuilder(context, SmsExpenseDatabase::class.java)
            .addCallback(SeedDatabaseCallback())
            .allowMainThreadQueries()
            .build()

        val bankRepo = BankRepositoryImpl(db.bankDao())
        val ruleRepo = SmsRuleRepositoryImpl(db.smsRuleDao())
        val txRepo = TransactionRepositoryImpl(db.transactionDao())
        val parseLogRepo = ParseLogRepositoryImpl(db.parseLogDao())
        val syncMetaRepo = SyncMetaRepositoryImpl(db.syncMetaDao())
        val categoryRepo = CategoryRepositoryImpl(db.categoryDao(), db.userCategoryRuleDao())
        val labelRepo = TransactionLabelRepositoryImpl(db.transactionLabelDao())
        val smsReader = SmsReader(ApplicationProvider.getApplicationContext<Context>().contentResolver)
        val demoPrefs = DemoDataPreferences(dataStore)

        useCase = SmsSyncUseCase(
            smsReader,
            ruleRepo,
            txRepo,
            parseLogRepo,
            syncMetaRepo,
            bankRepo,
            demoPrefs,
            categoryRepo,
            labelRepo,
            kotlinx.coroutines.Dispatchers.IO
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun handleIncomingSms_datesTransactionFromSmsTimestamp() = runBlocking {
        val body = "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2025-06-15:21:35:51.Not You? To Block+Reissue Call 18002586161"
        val inserted = useCase.handleIncomingSms(body, "AD-HDFCBK-S", oldEpochMillis)

        assertEquals(true, inserted)

        val stored = db.transactionDao().getAllTransactions().first()
        assertNotNull(stored.firstOrNull())
        val expected = Instant.ofEpochMilli(oldEpochMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay()
        assertEquals(expected, stored[0].transactionDate)
    }
}
