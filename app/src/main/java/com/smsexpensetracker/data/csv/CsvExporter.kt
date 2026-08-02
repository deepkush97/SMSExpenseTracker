package com.smsexpensetracker.data.csv

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.smsexpensetracker.core.csv.CsvCodec
import com.smsexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ExportResult(val uri: Uri, val fileName: String, val count: Int)

data class ExportFile(val file: File, val count: Int)

@Singleton
class CsvExporter @Inject constructor(
    private val context: Context,
    private val baseDir: File,
    private val transactionRepository: TransactionRepository
) {

    suspend fun exportAll(): ExportResult {
        val exportFile = buildExportFile()
        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, exportFile.file)
        return ExportResult(uri, exportFile.file.name, exportFile.count)
    }

    internal suspend fun buildExportFile(): ExportFile {
        val transactions = transactionRepository.getAllTransactions().first()
        val csv = CsvCodec.toCsv(transactions)
        val dir = File(baseDir, "exports").apply { mkdirs() }
        val fileName = "transactions_" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv"
        val file = File(dir, fileName)
        withContext(Dispatchers.IO) { file.writeText(csv, Charsets.UTF_8) }
        return ExportFile(file, transactions.size)
    }
}
