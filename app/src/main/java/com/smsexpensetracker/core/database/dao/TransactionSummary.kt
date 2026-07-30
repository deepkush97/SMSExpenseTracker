package com.smsexpensetracker.core.database.dao

import com.smsexpensetracker.core.database.entity.TransactionType

data class BankSummary(val bankId: Long, val type: TransactionType, val total: Long)
data class MonthlySummary(val yearMonth: String, val type: TransactionType, val total: Long)
data class CategorySummary(val categoryId: Long?, val total: Long)
