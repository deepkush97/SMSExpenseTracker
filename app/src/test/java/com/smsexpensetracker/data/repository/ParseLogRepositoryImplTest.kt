package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.ParseLogDao
import com.smsexpensetracker.core.database.entity.ParseLogEntity
import com.smsexpensetracker.core.database.entity.ParseStatus
import com.smsexpensetracker.domain.model.ParseLog
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class ParseLogRepositoryImplTest {
    private val parseLogDao = mockk<ParseLogDao>()
    private lateinit var repo: ParseLogRepositoryImpl

    @Before
    fun setup() {
        repo = ParseLogRepositoryImpl(parseLogDao)
    }

    @Test
    fun `getAllRules maps entities to domain models`() = runTest {
        val entities = listOf<ParseLogEntity>(
            ParseLogEntity(
                1,
                "some body",
                "HDFCBK",
                LocalDateTime.of(2026, 6, 15, 10, 0, 0),
                ParseStatus.SUCCESS
            ),
            ParseLogEntity(
                1, "some body 2", "ICICIB",
                LocalDateTime.of(2026, 6, 15, 11, 0, 0), ParseStatus.SUCCESS
            ),
        )

        every { parseLogDao.getAllLogs() } returns flowOf(entities)

        val result = repo.getAllLogs().first()

        assertEquals(2, result.size)
        result.forEachIndexed { index, res ->
            assertEquals(entities[index].id, res.id)
            assertEquals(entities[index].smsBody, res.smsBody)
            assertEquals(entities[index].smsSender, res.smsSender)
            assertEquals(entities[index].parsedAt, res.parsedAt)
            assertEquals(
                entities[index].status,
                com.smsexpensetracker.core.database.entity.ParseStatus.valueOf(res.status.name)
            )
        }
    }


    @Test
    fun `insert works`() = runTest {
        val entity = ParseLogEntity(
            1,
            "some body",
            "HDFCBK",
            LocalDateTime.of(2026, 6, 15, 10, 0, 0),
            ParseStatus.SUCCESS
        )
        coEvery { parseLogDao.insert(any<ParseLogEntity>()) } coAnswers { Unit }

        repo.insert(
            ParseLog(
                entity.id,
                entity.smsBody,
                entity.smsSender,
                entity.parsedAt,
                status = com.smsexpensetracker.domain.model.ParseStatus.valueOf(entity.status.name),
                errorMessage = entity.errorMessage
            )
        )

        coVerify { parseLogDao.insert(any()) }
    }

    @Test
    fun `deleteFailed delegates to dao`() = runTest {
        coEvery { parseLogDao.deleteFailed() } returns Unit

        repo.deleteFailed()

        coVerify { parseLogDao.deleteFailed() }
    }
}