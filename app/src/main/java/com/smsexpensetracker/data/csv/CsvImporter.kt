package com.smsexpensetracker.data.csv

import android.content.ContentResolver
import android.net.Uri
import com.smsexpensetracker.core.csv.CsvCodec
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.repository.BankRepository
import com.smsexpensetracker.domain.repository.CategoryRepository
import com.smsexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val imported: Int, val skipped: Int, val invalid: Int)

@Singleton
class CsvImporter @Inject constructor(
    private val contentResolver: ContentResolver,
    private val transactionRepository: TransactionRepository,
    private val bankRepository: BankRepository,
    private val categoryRepository: CategoryRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun importFrom(uri: Uri): ImportResult {
        val text = withContext(ioDispatcher) {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        }
        return importFromText(text)
    }

    internal suspend fun importFromText(text: String): ImportResult = withContext(ioDispatcher) {
        val rows = CsvCodec.parse(text)
        CsvCodec.requireHeader(rows)

        val seen = transactionRepository.getAllTransactions().first()
            .map { Triple(it.amount, it.transactionDate, it.description) }
            .toMutableSet()

        val bankIds = bankRepository.getAllBanks().first().map { it.id }.toSet()
        val categoryIds = categoryRepository.getAllCategories().first().map { it.id }.toSet()

        var skipped = 0
        var invalid = 0
        val candidates = mutableListOf<Transaction>()

        for (raw in rows.drop(1)) {
            val row = CsvCodec.toTransactionRow(raw)
            if (row == null || row.amount <= 0L || row.bankId == null) {
                invalid++
                continue
            }
            if (row.bankId !in bankIds) {
                invalid++
                continue
            }
            val safeCategoryId = row.categoryId?.takeIf { it in categoryIds }
            val key = Triple(row.amount, row.transactionDate, row.description)
            if (!seen.add(key)) {
                skipped++
                continue
            }
            candidates += Transaction(
                id = 0L,
                bankId = row.bankId,
                amount = row.amount,
                transactionType = row.type,
                description = row.description,
                transactionDate = row.transactionDate,
                categoryId = safeCategoryId,
                rawSms = row.rawSms,
                smsTimestamp = row.smsTimestamp,
                createdAt = LocalDateTime.now(),
                parseMethod = row.parseMethod
            )
        }

        val imported = if (candidates.isEmpty()) 0 else transactionRepository.insertBatch(candidates)
        ImportResult(imported = imported, skipped = skipped, invalid = invalid)
    }
}
