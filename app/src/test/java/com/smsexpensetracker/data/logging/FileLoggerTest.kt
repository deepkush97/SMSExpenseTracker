package com.smsexpensetracker.data.logging

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FileLoggerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val logger by lazy { FileLogger(mockk<Context>(), tempDir.root) }

    @Test
    fun `logFileName maps each file`() = runTest {
        assertEquals("error_log.txt", logger.logFileName(LogFile.ERROR_LOG))
        assertEquals("parse_failures.txt", logger.logFileName(LogFile.PARSE_FAILURES))
        assertEquals("unparsed_sms.txt", logger.logFileName(LogFile.UNPARSED_SMS))
        assertEquals("crash_log.txt", logger.logFileName(LogFile.CRASH_LOG))
    }

    @Test
    fun `append creates dir and file with timestamped line`() = runTest {
        logger.append(LogFile.ERROR_LOG, "boom")
        val file = File(tempDir.root, "logs/error_log.txt")
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.matches(Regex("""^\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\] boom\n$""")))
    }

    @Test
    fun `appendBlocking writes synchronously`() {
        logger.appendBlocking(LogFile.CRASH_LOG, "crash")
        val content = File(tempDir.root, "logs/crash_log.txt").readText()
        assertTrue(content.contains("crash"))
    }

    @Test
    fun `read missing file returns empty string`() = runTest {
        assertEquals("", logger.read(LogFile.PARSE_FAILURES))
    }

    @Test
    fun `read round-trips appended content`() = runTest {
        logger.append(LogFile.ERROR_LOG, "first")
        logger.append(LogFile.ERROR_LOG, "second")
        val content = logger.read(LogFile.ERROR_LOG)
        assertTrue(content.contains("first"))
        assertTrue(content.contains("second"))
    }

    @Test
    fun `readAll returns all four files`() = runTest {
        logger.append(LogFile.ERROR_LOG, "e")
        logger.append(LogFile.CRASH_LOG, "c")
        val all = logger.readAll()
        assertEquals(4, all.size)
        assertTrue(all.getValue(LogFile.ERROR_LOG).contains("e"))
        assertTrue(all.getValue(LogFile.CRASH_LOG).contains("c"))
        assertEquals("", all.getValue(LogFile.UNPARSED_SMS))
    }

    @Test
    fun `clear truncates the file`() = runTest {
        logger.append(LogFile.ERROR_LOG, "data")
        logger.clear(LogFile.ERROR_LOG)
        assertEquals("", logger.read(LogFile.ERROR_LOG))
    }

    @Test
    fun `logFile is under baseDir logs dir`() {
        assertEquals(File(tempDir.root, "logs/error_log.txt"), logger.logFile(LogFile.ERROR_LOG))
    }
}
