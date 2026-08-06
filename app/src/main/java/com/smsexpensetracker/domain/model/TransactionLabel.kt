package com.smsexpensetracker.domain.model

data class TransactionLabel(
    val id: Long,
    val transactionId: Long,
    val label: String
)
