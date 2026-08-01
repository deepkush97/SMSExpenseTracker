package com.smsexpensetracker.domain.usecase

import com.smsexpensetracker.data.sms.SmsMessage
import com.smsexpensetracker.data.sms.SmsReader
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.repository.ParseLogRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import com.smsexpensetracker.domain.repository.SyncMetaRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import com.smsexpensetracker.domain.value.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmsSyncUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var smsReader: SmsReader
    private lateinit var smsRuleRepository: SmsRuleRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var parseLogRepository: ParseLogRepository
    private lateinit var syncMetaRepository: SyncMetaRepository
    private lateinit var useCase: SmsSyncUseCase

    private val hdfcRule = SmsRule(
        id = 1L,
        bankId = 1L,
        pattern = "Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
        description = "HDFC CC Debit"
    )

    private val hdfcSms = SmsMessage(
        id = 10L,
        sender = "AD-HDFCBK-S",
        body = "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161",
        timestamp = 1750000000000L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        smsReader = mockk()
        smsRuleRepository = mockk()
        transactionRepository = mockk()
        parseLogRepository = mockk()
        syncMetaRepository = mockk()
        useCase = SmsSyncUseCase(
            smsReader,
            smsRuleRepository,
            transactionRepository,
            parseLogRepository,
            syncMetaRepository,
            testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sync parses HDFC sms and inserts a transaction`() = runTest {
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatch(any()) } returns 1
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertEquals(SyncResult(scanned = 1, inserted = 1, unparsed = 0), result)
        coVerify {
            transactionRepository.insertBatch(
                match { list ->
                    list.size == 1 &&
                        list[0].amount == 483176L &&
                        list[0].bankId == 1L &&
                        list[0].rawSms == hdfcSms.body
                }
            )
        }
        coVerify(exactly = 0) { parseLogRepository.insert(any()) }
    }

    @Test
    fun `sync ignores inactive rules`() = runTest {
        val inactiveHdfc = hdfcRule.copy(id = 99L, isActive = false)
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(inactiveHdfc))
        coEvery { transactionRepository.insertBatch(any()) } returns 0
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertEquals(SyncResult(scanned = 1, inserted = 0, unparsed = 1), result)
        coVerify(exactly = 0) { transactionRepository.insertBatch(any()) }
    }

    @Test
    fun `sync records parse log for unparsed sms`() = runTest {
        val junk = SmsMessage(11L, "UNKNOWN", "This is not a bank SMS", 1750000000001L)
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(junk))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(emptyList())
        coEvery { transactionRepository.insertBatch(any()) } returns 0
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertEquals(SyncResult(scanned = 1, inserted = 0, unparsed = 1), result)
        coVerify {
            parseLogRepository.insert(
                match { log -> log.status == ParseStatus.FAILED && log.smsBody == junk.body }
            )
        }
        coVerify(exactly = 0) { transactionRepository.insertBatch(any()) }
    }

    @Test
    fun `sync emits progress for processed messages`() = runTest {
        val messages = listOf(
            hdfcSms,
            SmsMessage(11L, "UNKNOWN", "This is not a bank SMS", 1750000000001L),
            hdfcSms.copy(id = 12L)
        )
        coEvery { smsReader.readSms() } returns MutableStateFlow(messages)
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatch(any()) } returns 1
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        useCase.sync()

        val p = useCase.progress.value
        assertEquals(3, p.processed)
        assertEquals(3, p.total)
        assertEquals(1, p.unparsed)
    }

    @Test
    fun `sync upserts sync meta on success`() = runTest {
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatch(any()) } returns 1
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        useCase.sync()

        coVerify(exactly = 1) { syncMetaRepository.upsert(any()) }
    }

    @Test
    fun `second sync while running returns empty result`() = runTest {
        val never = flow<List<SmsMessage>> { awaitCancellation() }
        coEvery { smsReader.readSms() } returns never
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))

        val first = backgroundScope.launch { useCase.sync() }
        runCurrent()
        val second = useCase.sync()

        assertEquals(SyncResult(), second)
        first.cancel()
    }

    @Test
    fun `sync returns error when insert batch fails`() = runTest {
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatch(any()) } throws RuntimeException("db down")
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertNotNull(result.error)
    }
}
