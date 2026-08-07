package com.smsexpensetracker.domain.usecase

import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.data.repository.TransactionLabelRepositoryImpl
import com.smsexpensetracker.data.sms.SmsMessage
import com.smsexpensetracker.data.sms.SmsReader
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.model.SmsRule
import com.smsexpensetracker.domain.model.UserCategoryRule
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.ParseLogRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import com.smsexpensetracker.domain.repository.SyncMetaRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import com.smsexpensetracker.domain.value.SyncRange
import com.smsexpensetracker.domain.value.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class SmsSyncUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var smsReader: SmsReader
    private lateinit var smsRuleRepository: SmsRuleRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var parseLogRepository: ParseLogRepository
    private lateinit var syncMetaRepository: SyncMetaRepository
    private lateinit var bankRepository: BankRepository
    private lateinit var demoDataPreferences: DemoDataPreferences
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var transactionLabelRepository: TransactionLabelRepositoryImpl
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
        bankRepository = mockk()
        demoDataPreferences = mockk()
        categoryRepository = mockk()
        transactionLabelRepository = mockk()
        every { demoDataPreferences.demoDataLoaded } returns flowOf(false)
        every { bankRepository.getAllBanks() } returns flowOf(listOf(Bank(1L, "HDFC Bank", "HDFCBK")))
        every { categoryRepository.getRules() } returns flowOf(emptyList())
        every { categoryRepository.getAllCategories() } returns flowOf(emptyList())
        useCase = SmsSyncUseCase(
            smsReader,
            smsRuleRepository,
            transactionRepository,
            parseLogRepository,
            syncMetaRepository,
            bankRepository,
            demoDataPreferences,
            categoryRepository,
            transactionLabelRepository,
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
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(1L)
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertEquals(SyncResult(scanned = 1, inserted = 1, unparsed = 0), result)
        coVerify {
            transactionRepository.insertBatchReturningIds(
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
    fun `sync dates the transaction from the sms timestamp not now`() = runTest {
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(1L)
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        useCase.sync()

        val expectedDate = Instant.ofEpochMilli(hdfcSms.timestamp)
            .atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay()
        coVerify {
            transactionRepository.insertBatchReturningIds(
                match { list -> list[0].transactionDate == expectedDate }
            )
        }
    }

    @Test
    fun `sync ignores inactive rules`() = runTest {
        val inactiveHdfc = hdfcRule.copy(id = 99L, isActive = false)
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(inactiveHdfc))
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertEquals(SyncResult(scanned = 1, inserted = 0, unparsed = 1), result)
        coVerify(exactly = 0) { transactionRepository.insertBatchReturningIds(any()) }
    }

    @Test
    fun `sync records parse log for unparsed sms`() = runTest {
        val junk = SmsMessage(11L, "UNKNOWN", "This is not a bank SMS", 1750000000001L)
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(junk))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(emptyList())
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns emptyList()
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertEquals(SyncResult(scanned = 1, inserted = 0, unparsed = 1), result)
        coVerify {
            parseLogRepository.insert(
                match { log -> log.status == ParseStatus.FAILED && log.smsBody == junk.body }
            )
        }
        coVerify(exactly = 0) { transactionRepository.insertBatchReturningIds(any()) }
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
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(1L)
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
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(1L)
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
        coEvery { transactionRepository.insertBatchReturningIds(any()) } throws RuntimeException("db down")
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertNotNull(result.error)
    }

    @Test
    fun `sync returns backstop error when demo data is loaded`() = runTest {
        every { demoDataPreferences.demoDataLoaded } returns MutableStateFlow(true)

        val result = useCase.sync()

        assertEquals(
            SyncResult(error = "Delete demo data before syncing real SMS."),
            result
        )
        coVerify(exactly = 0) { smsReader.readSms() }
    }

    @Test
    fun `handleIncomingSms returns false when demo data is loaded`() = runTest {
        every { demoDataPreferences.demoDataLoaded } returns MutableStateFlow(true)

        val result = useCase.handleIncomingSms(hdfcSms.body, hdfcSms.sender, hdfcSms.timestamp)

        assertEquals(false, result)
        coVerify(exactly = 0) { bankRepository.getAllBanks() }
        coVerify(exactly = 0) { transactionRepository.insertBatchReturningIds(any()) }
    }

    @Test
    fun `handleIncomingSms ignores non-bank sender without a parse log`() = runTest {
        val result = useCase.handleIncomingSms("Your OTP is 1234", "VM-OTPSVC", System.currentTimeMillis())

        assertEquals(false, result)
        coVerify(exactly = 0) { parseLogRepository.insert(any()) }
        coVerify(exactly = 0) { transactionRepository.insertBatchReturningIds(any()) }
        coVerify(exactly = 0) { syncMetaRepository.upsert(any()) }
    }

    @Test
    fun `handleIncomingSms inserts transaction for bank sms`() = runTest {
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(1L)
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.handleIncomingSms(hdfcSms.body, hdfcSms.sender, hdfcSms.timestamp)

        assertEquals(true, result)
        coVerify {
            transactionRepository.insertBatchReturningIds(
                match { list ->
                    list.size == 1 &&
                        list[0].amount == 483176L &&
                        list[0].bankId == 1L &&
                        list[0].smsTimestamp == hdfcSms.timestamp &&
                        list[0].parseMethod == ParseMethod.SMS
                }
            )
        }
        coVerify(exactly = 0) { parseLogRepository.insert(any()) }
        coVerify(exactly = 1) { syncMetaRepository.upsert(any()) }
    }

    @Test
    fun `handleIncomingSms records failed parse log and returns false`() = runTest {
        val body = "Rs. 100 debited from A/c for something"
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(emptyList())
        coEvery { parseLogRepository.insert(any()) } returns Unit

        val result = useCase.handleIncomingSms(body, "AD-HDFCBK-S", 1750000000000L)

        assertEquals(false, result)
        coVerify {
            parseLogRepository.insert(
                match { log -> log.status == ParseStatus.FAILED && log.smsBody == body }
            )
        }
        coVerify(exactly = 0) { transactionRepository.insertBatchReturningIds(any()) }
        coVerify(exactly = 0) { syncMetaRepository.upsert(any()) }
    }

    @Test
    fun `handleIncomingSms returns false when insert is deduplicated`() = runTest {
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns emptyList()

        val result = useCase.handleIncomingSms(hdfcSms.body, hdfcSms.sender, hdfcSms.timestamp)

        assertEquals(false, result)
        coVerify(exactly = 0) { syncMetaRepository.upsert(any()) }
    }

    @Test
    fun `handleIncomingSms returns false when repository throws`() = runTest {
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatchReturningIds(any()) } throws RuntimeException("db down")

        val result = useCase.handleIncomingSms(hdfcSms.body, hdfcSms.sender, hdfcSms.timestamp)

        assertEquals(false, result)
    }

    @Test
    fun `handleIncomingSms captures unrecognized-sender bank sms via rule match`() = runTest {
        val iciciRule = SmsRule(
            id = 2L,
            bankId = 2L,
            pattern = "ICICI Bank Acct \\w+ debited for Rs ([\\d,.]+) on [\\w-]+; (.+?) credited\\. UPI",
            description = "ICICI UPI Debit"
        )
        val body = "ICICI Bank Acct XX123 debited for Rs 242.00 on 26-Jul-26; BUS Ticket credited. UPI:003637672623. Call 18002662 for dispute. SMS BLOCK 796 to 9215676766."
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(iciciRule))
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(1L)
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.handleIncomingSms(body, "AD-ICICIT-S", 1750000000000L)

        assertEquals(true, result)
        coVerify {
            transactionRepository.insertBatchReturningIds(
                match { list -> list.size == 1 && list[0].bankId == 2L }
            )
        }
        coVerify(exactly = 0) { parseLogRepository.insert(any()) }
    }

    @Test
    fun `concurrent sync and handleIncomingSms both complete`() = runTest {
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(1L)
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val syncDeferred = backgroundScope.async { useCase.sync() }
        val incomingDeferred = backgroundScope.async {
            useCase.handleIncomingSms(hdfcSms.body, hdfcSms.sender, hdfcSms.timestamp)
        }
        runCurrent()
        advanceUntilIdle()

        val syncResult = syncDeferred.await()
        val incomingResult = incomingDeferred.await()

        assertEquals(1, syncResult.scanned)
        assertEquals(1, syncResult.inserted)
        assertEquals(true, incomingResult)
    }

    @Test
    fun `handleIncomingSms returns true when upsert throws after insert`() = runTest {
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(1L)
        coEvery { syncMetaRepository.upsert(any()) } throws RuntimeException("meta down")

        val result = useCase.handleIncomingSms(hdfcSms.body, hdfcSms.sender, hdfcSms.timestamp)

        assertEquals(true, result)
    }

    @Test
    fun `sync with a range passes date range to readSms`() = runTest {
        coEvery { smsReader.readSms(dateRange = any()) } returns MutableStateFlow(emptyList())
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(emptyList())
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        useCase.sync(range = SyncRange.LAST_1W)

        coVerify {
            smsReader.readSms(
                dateRange = match { pair ->
                    pair != null && pair.second > pair.first && pair.second <= System.currentTimeMillis()
                }
            )
        }
    }

    @Test
    fun `sync with ALL range keeps full scan (null date range)`() = runTest {
        coEvery { smsReader.readSms(dateRange = null) } returns MutableStateFlow(emptyList())
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(emptyList())
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        useCase.sync(range = SyncRange.ALL)

        coVerify(exactly = 1) { smsReader.readSms(dateRange = null) }
    }

    @Test
    fun `sync applies category rule and records label`() = runTest {
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        every { categoryRepository.getRules() } returns flowOf(
            listOf(UserCategoryRule(id = 1L, pattern = "acme", categoryId = 7L))
        )
        every { categoryRepository.getAllCategories() } returns flowOf(
            listOf(Category(id = 7L, name = "Shopping", icon = "", color = 0, isDefault = false))
        )
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(101L)
        coEvery { transactionLabelRepository.insert(any()) } returns 1L
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        val result = useCase.sync()

        assertEquals(SyncResult(scanned = 1, inserted = 1, unparsed = 0), result)
        coVerify {
            transactionRepository.insertBatchReturningIds(
                match { list -> list.size == 1 && list[0].categoryId == 7L }
            )
        }
        coVerify {
            transactionLabelRepository.insert(
                match { label -> label.transactionId == 101L && label.label == "Shopping" }
            )
        }
    }

    @Test
    fun `sync does not record label when no rule matches`() = runTest {
        coEvery { smsReader.readSms() } returns MutableStateFlow(listOf(hdfcSms))
        every { smsRuleRepository.getAllRules() } returns MutableStateFlow(listOf(hdfcRule))
        every { categoryRepository.getRules() } returns flowOf(emptyList())
        every { categoryRepository.getAllCategories() } returns flowOf(emptyList())
        coEvery { transactionRepository.insertBatchReturningIds(any()) } returns listOf(101L)
        coEvery { parseLogRepository.insert(any()) } returns Unit
        coEvery { syncMetaRepository.upsert(any()) } returns Unit

        useCase.sync()

        coVerify(exactly = 0) { transactionLabelRepository.insert(any()) }
    }
}
