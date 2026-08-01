package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.BankDao
import com.smsexpensetracker.core.database.entity.BankEntity
import com.smsexpensetracker.domain.model.Bank
import com.smsexpensetracker.domain.repository.BankRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BankRepositoryImpl @Inject constructor(
    private val bankDao: BankDao
) : BankRepository {
    override fun getAllBanks(): Flow<List<Bank>> =
        bankDao.getAllBanks().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getBankById(id: Long): Bank? =
        bankDao.getBankById(id)?.toDomain()


    override suspend fun getBankBySender(sender: String): Bank? =
        bankDao.getBankBySmsSender(sender)?.toDomain()

    override suspend fun insert(bank: Bank): Long =
        bankDao.insert(bank.toEntity())

    override suspend fun update(bank: Bank) {
        bankDao.update(bank.toEntity())
    }

    override suspend fun delete(bank: Bank) {
        bankDao.delete(bank.toEntity())
    }

    override suspend fun countTransactions(bankId: Long): Int =
        bankDao.getTransactionCount(bankId)

    private fun BankEntity.toDomain() = Bank(id = id, name = name, smsSender = smsSender)

    private fun Bank.toEntity() = BankEntity(id = id, name = name, smsSender = smsSender)

}