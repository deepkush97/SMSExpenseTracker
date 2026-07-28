package com.smsexpensetracker.domain.repository

import com.smsexpensetracker.domain.model.Bank
import kotlinx.coroutines.flow.Flow

interface BankRepository {
    fun getAllBanks(): Flow<List<Bank>>
    suspend fun getBankById(id: Long): Bank?
    suspend fun getBankBySender(sender: String): Bank?
}