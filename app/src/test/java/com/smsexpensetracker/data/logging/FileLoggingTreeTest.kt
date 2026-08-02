package com.smsexpensetracker.data.logging

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FileLoggingTreeTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val logger by lazy { FileLogger(mockk<Context>(), tempDir.root) }
    private lateinit var tree: FileLoggingTree

    @Before
    fun setup() {
        tree = FileLoggingTree(logger)
        Timber.plant(tree)
    }

    @After
    fun teardown() {
        Timber.uprootAll()
    }

    @Test
    fun `error priority writes to error_log`() = runTest {
        Timber.e("boom")
        assertTrue(File(tempDir.root, "logs/error_log.txt").readText().contains("boom"))
    }

    @Test
    fun `warn priority writes to error_log`() = runTest {
        Timber.w("careful")
        assertTrue(File(tempDir.root, "logs/error_log.txt").readText().contains("careful"))
    }

    @Test
    fun `parse tag writes to parse_failures`() = runTest {
        Timber.tag("PARSE").w("parse failed")
        assertTrue(File(tempDir.root, "logs/parse_failures.txt").readText().contains("parse failed"))
    }

    @Test
    fun `unparsed tag writes to unparsed_sms`() = runTest {
        Timber.tag("UNPARSED").w("no match")
        assertTrue(File(tempDir.root, "logs/unparsed_sms.txt").readText().contains("no match"))
    }

    @Test
    fun `debug priority writes no file`() = runTest {
        Timber.d("noise")
        assertFalse(File(tempDir.root, "logs/error_log.txt").exists())
    }

    @Test
    fun `parse tag at debug priority still routes to parse_failures`() = runTest {
        Timber.tag("PARSE").d("low priority parse note")
        assertTrue(File(tempDir.root, "logs/parse_failures.txt").readText().contains("low priority parse note"))
    }
}
