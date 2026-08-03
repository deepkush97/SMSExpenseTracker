package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.dao.TransactionDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoDataSeederTest {

    private val transactionDao = mockk<TransactionDao>()

    @Test
    fun `seeds when table is empty and returns inserted count`() = runTest {
        coEvery { transactionDao.count() } returns 0
        coEvery { transactionDao.insertAll(any()) } returns Unit
        val inserted = DemoDataSeeder(transactionDao).seedIfEmpty()
        coVerify { transactionDao.insertAll(any()) }
        assertEquals(DemoTransactionGenerator.generate().size, inserted)
    }

    @Test
    fun `skips when table has rows and returns zero`() = runTest {
        coEvery { transactionDao.count() } returns 5
        val inserted = DemoDataSeeder(transactionDao).seedIfEmpty()
        coVerify(exactly = 0) { transactionDao.insertAll(any()) }
        assertEquals(0, inserted)
    }
}
