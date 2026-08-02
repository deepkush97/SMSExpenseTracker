package com.smsexpensetracker.data.logging

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoggingSetup @Inject constructor(
    private val fileLogger: FileLogger
) {

    fun install() {
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(FileLoggingTree(fileLogger))
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            fileLogger.appendBlocking(
                LogFile.CRASH_LOG,
                "Thread: ${thread.name}\n${throwable.stackTraceToString()}"
            )
            previous?.uncaughtException(thread, throwable)
        }
    }
}
