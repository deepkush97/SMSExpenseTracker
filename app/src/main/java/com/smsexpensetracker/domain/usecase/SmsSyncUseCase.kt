package com.smsexpensetracker.domain.usecase

import com.smsexpensetracker.core.parser.ParserEngine
import com.smsexpensetracker.data.sms.SmsReader
import com.smsexpensetracker.domain.model.ParseLog
import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.ParseStatus
import com.smsexpensetracker.domain.model.SyncMeta
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.repository.ParseLogRepository
import com.smsexpensetracker.domain.repository.SmsRuleRepository
import com.smsexpensetracker.domain.repository.SyncMetaRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import com.smsexpensetracker.domain.value.SyncProgress
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

@Singleton
class SmsSyncUseCase @Inject constructor(
    private val smsReader: SmsReader,
    private val smsRuleRepository: SmsRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val parseLogRepository: ParseLogRepository,
    private val syncMetaRepository: SyncMetaRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    private var isRunning = false

    suspend fun sync(): SyncResult {
        if (isRunning) return SyncResult()
        isRunning = true
        _progress.value = SyncProgress()
        return try {
            withContext(ioDispatcher) {
                val rules = smsRuleRepository.getAllRules().first().filter { it.isActive }
                val messages = smsReader.readSms().first()
                val total = messages.size

                var processed = 0
                var unparsed = 0
                var inserted = 0

                messages.chunked(100).forEach { chunk ->
                    val transactions = mutableListOf<Transaction>()
                    for (msg in chunk) {
                        val parsed = ParserEngine.parse(
                            msg.body,
                            msg.sender,
                            rules.map { it.bankId to it.pattern }
                        )
                        if (parsed.errorMessage != null) {
                            Timber.tag("PARSE").w(
                                "Parse failed [${msg.sender}]: ${parsed.errorMessage}"
                            )
                            parseLogRepository.insert(
                                ParseLog(
                                    id = 0L,
                                    smsBody = msg.body,
                                    smsSender = msg.sender,
                                    parsedAt = LocalDateTime.now(),
                                    status = ParseStatus.FAILED,
                                    errorMessage = parsed.errorMessage
                                )
                            )
                            unparsed++
                        } else if (parsed.bankId != null && parsed.amount > 0L) {
                            transactions += Transaction(
                                id = 0L,
                                bankId = parsed.bankId,
                                amount = parsed.amount,
                                transactionType = parsed.type,
                                description = parsed.description,
                                transactionDate = LocalDate.now().atStartOfDay(),
                                categoryId = null,
                                rawSms = msg.body,
                                smsTimestamp = msg.timestamp,
                                createdAt = LocalDateTime.now(),
                                parseMethod = ParseMethod.SMS
                            )
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
}
