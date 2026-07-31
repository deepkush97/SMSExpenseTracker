package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.dao.TransactionDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DemoDataSeederTest {

    private val transactionDao = mockk<TransactionDao>()

    @Test
    fun `seeds when table is empty`() = runTest {
        coEvery { transactionDao.count() } returns 0
        coEvery { transactionDao.insertAll(any()) } coAnswers { Unit }
        DemoDataSeeder(transactionDao).seedIfEmpty()
        coVerify { transactionDao.insertAll(any()) }
    }

    @Test
    fun `skips when table has rows`() = runTest {
        coEvery { transactionDao.count() } returns 5
        DemoDataSeeder(transactionDao).seedIfEmpty()
        coVerify(exactly = 0) { transactionDao.insertAll(any()) }
    }
}
