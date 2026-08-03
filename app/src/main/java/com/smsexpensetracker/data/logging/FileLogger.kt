package com.smsexpensetracker.data.logging

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.smsexpensetracker.data.csv.FILE_PROVIDER_AUTHORITY
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class LogFile { ERROR_LOG, PARSE_FAILURES, UNPARSED_SMS, CRASH_LOG }

@Singleton
class FileLogger @Inject constructor(
    private val context: Context,
    private val baseDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun logFileName(file: LogFile): String = when (file) {
        LogFile.ERROR_LOG -> "error_log.txt"
        LogFile.PARSE_FAILURES -> "parse_failures.txt"
        LogFile.UNPARSED_SMS -> "unparsed_sms.txt"
        LogFile.CRASH_LOG -> "crash_log.txt"
    }

    fun logFile(file: LogFile): File = File(File(baseDir, "logs"), logFileName(file))

    fun appendBlocking(file: LogFile, line: String) {
        val target = logFile(file)
        target.parentFile?.mkdirs()
        val timestamp = LocalDateTime.now().format(timestampFormat)
        target.appendText("[$timestamp] $line\n", Charsets.UTF_8)
    }

    suspend fun append(file: LogFile, line: String) = withContext(ioDispatcher) {
        appendBlocking(file, line)
    }

    suspend fun read(file: LogFile): String = withContext(ioDispatcher) {
        val target = logFile(file)
        if (target.exists()) target.readText(Charsets.UTF_8) else ""
    }

    suspend fun readAll(): Map<LogFile, String> = withContext(ioDispatcher) {
        LogFile.entries.associateWith { file ->
            val target = logFile(file)
            if (target.exists()) target.readText(Charsets.UTF_8) else ""
        }
    }

    suspend fun clear(file: LogFile) = withContext(ioDispatcher) {
        logFile(file).writeText("", Charsets.UTF_8)
    }

    fun logFileUri(file: LogFile): Uri =
        FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, logFile(file))
}
