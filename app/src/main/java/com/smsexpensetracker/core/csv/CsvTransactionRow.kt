package com.smsexpensetracker.core.csv

import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.TransactionType
import java.time.LocalDateTime

data class CsvTransactionRow(
    val amount: Long,
    val type: TransactionType,
    val description: String,
    val transactionDate: LocalDateTime,
    val bankId: Long?,
    val categoryId: Long?,
    val smsTimestamp: Long,
    val parseMethod: ParseMethod,
    val rawSms: String
)
