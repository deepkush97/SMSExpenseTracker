package com.smsexpensetracker.domain.repository

import com.smsexpensetracker.domain.model.TransactionLabel

interface TransactionLabelRepository {
    suspend fun insert(label: TransactionLabel): Long
}
