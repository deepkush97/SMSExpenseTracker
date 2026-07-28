package com.smsexpensetracker.domain.model

import java.time.LocalDateTime

enum class TransactionType { CREDIT, DEBIT }

data class Transaction(
    val id: Long,
    val bankId: Long,
    val amount: Long,
    val transactionType: TransactionType,
    val description: String,
    val transactionDate: LocalDateTime,
    val categoryId: Long?,
    val rawSms: String,
    val smsTimestamp: Long,
    val createdAt: LocalDateTime
)