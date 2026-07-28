package com.smsexpensetracker.domain.value

import com.smsexpensetracker.domain.model.TransactionType

data class ParsedResult(
    val amount: Long,
    val type: TransactionType,
    val description: String,
    val bankId: Long?,
    val confidence: Float,
    val rawSms: String,
    val errorMessage: String? = null
)