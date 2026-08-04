package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.dao.TransactionDao
import com.smsexpensetracker.core.settings.DemoDataPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoDataSeederTest {

    private val transactionDao = mockk<TransactionDao>()
    private val demoDataPreferences = mockk<DemoDataPreferences>()

    @Test
    fun `seeds when table is empty and sets demo flag`() = runTest {
        coEvery { transactionDao.count() } returns 0
        coEvery { transactionDao.insertAll(any()) } returns Unit
        coEvery { demoDataPreferences.setDemoDataLoaded(true) } returns Unit
        val inserted = DemoDataSeeder(transactionDao, demoDataPreferences).seedIfEmpty()
        coVerify { transactionDao.insertAll(any()) }
        coVerify(exactly = 1) { demoDataPreferences.setDemoDataLoaded(true) }
        assertEquals(DemoTransactionGenerator.generate().size, inserted)
    }

    @Test
    fun `skips when table has rows and does not set demo flag`() = runTest {
        coEvery { transactionDao.count() } returns 5
        val inserted = DemoDataSeeder(transactionDao, demoDataPreferences).seedIfEmpty()
        coVerify(exactly = 0) { transactionDao.insertAll(any()) }
        coVerify(exactly = 0) { demoDataPreferences.setDemoDataLoaded(true) }
        assertEquals(0, inserted)
    }

    @Test
    fun `deleteDemoData wipes transactions and clears the demo flag`() = runTest {
        coEvery { transactionDao.deleteAll() } returns Unit
        coEvery { demoDataPreferences.setDemoDataLoaded(false) } returns Unit

        DemoDataSeeder(transactionDao, demoDataPreferences).deleteDemoData()

        coVerify(exactly = 1) { transactionDao.deleteAll() }
        coVerify(exactly = 1) { demoDataPreferences.setDemoDataLoaded(false) }
    }
}
