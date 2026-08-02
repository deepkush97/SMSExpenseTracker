package com.smsexpensetracker.data.logging

import android.util.Log
import timber.log.Timber

class FileLoggingTree(private val logger: FileLogger) : Timber.DebugTree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val target = when {
            tag == "PARSE" -> LogFile.PARSE_FAILURES
            tag == "UNPARSED" -> LogFile.UNPARSED_SMS
            priority == Log.ERROR || priority == Log.WARN -> LogFile.ERROR_LOG
            else -> return
        }
        logger.appendBlocking(target, message)
    }
}
