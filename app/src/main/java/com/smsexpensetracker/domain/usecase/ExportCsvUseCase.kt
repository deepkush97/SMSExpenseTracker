package com.smsexpensetracker.domain.usecase

import com.smsexpensetracker.data.csv.CsvExporter
import com.smsexpensetracker.data.csv.ExportResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportCsvUseCase @Inject constructor(
    private val exporter: CsvExporter
) {
    suspend operator fun invoke(): Result<ExportResult> = try {
        Result.success(exporter.exportAll())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
