package com.smsexpensetracker.domain.usecase

import com.smsexpensetracker.core.parser.ParserEngine
import com.smsexpensetracker.core.parser.detectBankForSender
import com.smsexpensetracker.core.settings.DemoDataPreferences
import com.smsexpensetracker.data.sms.SmsReader
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.model.SyncMeta
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.ParseLogRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import com.smsexpensetracker.domain.repository.SyncMetaRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import com.smsexpensetracker.domain.value.SyncProgress
import com.smsexpensetracker.domain.value.SyncRange
import com.smsexpensetracker.domain.value.SyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

private sealed interface ClassifyResult {
    data class TransactionReady(val transaction: Transaction) : ClassifyResult
    data object ParseFailed : ClassifyResult
    data object Skipped : ClassifyResult
}

@Singleton
class SmsSyncUseCase @Inject constructor(
    private val smsReader: SmsReader,
    private val smsRuleRepository: SmsRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val parseLogRepository: ParseLogRepository,
    private val syncMetaRepository: SyncMetaRepository,
    private val bankRepository: BankRepository,
    private val demoDataPreferences: DemoDataPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    private var isRunning = false

    suspend fun sync(range: SyncRange? = null): SyncResult {
        if (isRunning) return SyncResult()
        if (demoDataPreferences.demoDataLoaded.first()) {
            return SyncResult(error = "Delete demo data before syncing real SMS.")
        }
        isRunning = true
        _progress.value = SyncProgress()
        return try {
            withContext(ioDispatcher) {
                val rules = smsRuleRepository.getAllRules().first().filter { it.isActive }
                val rulePairs = rules.map { it.bankId to it.pattern }
                val dateRange = range?.takeUnless { it == SyncRange.ALL }
                    ?.let { it.startTimestamp to it.endTimestamp }
                val messages = smsReader.readSms(dateRange = dateRange).first()
                val total = messages.size

                var processed = 0
                var unparsed = 0
                var inserted = 0

                messages.chunked(100).forEach { chunk ->
                    val transactions = mutableListOf<Transaction>()
                    for (msg in chunk) {
                        when (val result = classifySms(msg.body, msg.sender, msg.timestamp, rulePairs)) {
                            is ClassifyResult.TransactionReady -> transactions += result.transaction
                            ClassifyResult.ParseFailed -> unparsed++
                            ClassifyResult.Skipped -> Unit
                        }
                        processed++
                    }
                    if (transactions.isNotEmpty()) {
                        inserted += transactionRepository.insertBatch(transactions)
                    }
                    _progress.value = SyncProgress(
                        processed = processed,
                        total = total,
                        unparsed = unparsed
                    )
                }

                syncMetaRepository.upsert(
                    SyncMeta(
                        lastSyncTimestamp = System.currentTimeMillis(),
                        lastSmsId = null
                    )
                )

                SyncResult(scanned = total, inserted = inserted, unparsed = unparsed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult(error = "Sync failed: ${e.message ?: "unknown error"}")
        } finally {
            isRunning = false
        }
    }

    suspend fun handleIncomingSms(body: String, sender: String, timestamp: Long): Boolean {
        try {
            return withContext(ioDispatcher) {
                if (demoDataPreferences.demoDataLoaded.first()) return@withContext false
                val banks = bankRepository.getAllBanks().first()
                val knownBank = detectBankForSender(sender, banks) != null

                val rulePairs = smsRuleRepository.getAllRules().first()
                    .filter { it.isActive }
                    .map { it.bankId to it.pattern }

                val inserted = when (val result = classifySms(
                    body,
                    sender,
                    timestamp,
                    rulePairs,
                    writeParseLog = knownBank
                )) {
                    is ClassifyResult.TransactionReady ->
                        transactionRepository.insertBatch(listOf(result.transaction))
                    ClassifyResult.ParseFailed, ClassifyResult.Skipped -> 0
                }

                if (inserted > 0) {
                    try {
                        syncMetaRepository.upsert(
                            SyncMeta(
                                lastSyncTimestamp = System.currentTimeMillis(),
                                lastSmsId = null
                            )
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("PARSE").w(e, "sync meta upsert failed after insert")
                    }
                }
                inserted > 0
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("PARSE").e(e, "handleIncomingSms failed")
            return false
        }
    }

    private suspend fun classifySms(
        body: String,
        sender: String,
        timestamp: Long,
        rulePairs: List<Pair<Long, String>>,
        writeParseLog: Boolean = true
    ): ClassifyResult {
        val parsed = ParserEngine.parse(body, sender, rulePairs)
        if (parsed.errorMessage != null) {
            Timber.tag("PARSE").w("Parse failed [$sender]: ${parsed.errorMessage}")
            if (writeParseLog) {
                parseLogRepository.insert(
                    ParseLog(
                        id = 0L,
                        smsBody = body,
                        smsSender = sender,
                        parsedAt = LocalDateTime.now(),
                        status = ParseStatus.FAILED,
                        errorMessage = parsed.errorMessage
                    )
                )
            }
            return ClassifyResult.ParseFailed
        }
        if (parsed.bankId != null && parsed.amount > 0L) {
            return ClassifyResult.TransactionReady(
                Transaction(
                    id = 0L,
                    bankId = parsed.bankId,
                    amount = parsed.amount,
                    transactionType = parsed.type,
                    description = parsed.description,
                    transactionDate = LocalDate.now().atStartOfDay(),
                    categoryId = null,
                    rawSms = body,
                    smsTimestamp = timestamp,
                    createdAt = LocalDateTime.now(),
                    parseMethod = ParseMethod.SMS
                )
            )
        }
        return ClassifyResult.Skipped
    }
}
