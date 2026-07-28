package com.smsexpensetracker.core.parser

import com.smsexpensetracker.domain.model.SenderId

object SenderDetector {
    fun detect(sender: String): SenderId {
        val cleaned = cleanTraiPrefix(sender)
        return SenderId(cleaned)
    }

    private fun cleanTraiPrefix(raw: String): String {
        val parts = raw.split("-")
        return parts.firstOrNull { it.length >= 3 && it.all { c -> c.isLetterOrDigit() } }
            ?: raw
    }
}