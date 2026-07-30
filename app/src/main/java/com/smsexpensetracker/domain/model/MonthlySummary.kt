package com.smsexpensetracker.domain.model

data class MonthlySummary(val yearMonth: String, val type: TransactionType, val total: Long)
