package com.smsexpensetracker.domain.model

import java.time.LocalDateTime

enum class ParseStatus { SUCCESS, FAILED, SKIPPED }

data class ParseLog(
    val id: Long,
    val smsBody: String,
    val smsSender: String,
    val parsedAt: LocalDateTime,
    val status: ParseStatus,
    val errorMessage: String?
)