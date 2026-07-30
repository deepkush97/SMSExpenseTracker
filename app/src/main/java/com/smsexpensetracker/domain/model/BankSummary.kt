package com.smsexpensetracker.domain.model

data class BankSummary(val bankId: Long, val type: TransactionType, val total: Long)
