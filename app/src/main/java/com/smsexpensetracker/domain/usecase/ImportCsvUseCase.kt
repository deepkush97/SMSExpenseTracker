package com.smsexpensetracker.domain.usecase

import android.net.Uri
import com.smsexpensetracker.data.csv.CsvImporter
import com.smsexpensetracker.data.csv.ImportResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportCsvUseCase @Inject constructor(
    private val importer: CsvImporter
) {
    suspend operator fun invoke(uri: Uri): Result<ImportResult> = try {
        Result.success(importer.importFrom(uri))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
