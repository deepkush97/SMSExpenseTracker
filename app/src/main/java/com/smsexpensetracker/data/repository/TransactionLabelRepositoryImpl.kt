package com.smsexpensetracker.data.repository

import com.smsexpensetracker.core.database.dao.TransactionLabelDao
import com.smsexpensetracker.core.database.entity.TransactionLabelEntity
import com.smsexpensetracker.domain.model.TransactionLabel
import com.smsexpensetracker.domain.repository.TransactionLabelRepository
import javax.inject.Inject

class TransactionLabelRepositoryImpl @Inject constructor(
    private val transactionLabelDao: TransactionLabelDao
) : TransactionLabelRepository {
    override suspend fun insert(label: TransactionLabel): Long =
        transactionLabelDao.insert(
            TransactionLabelEntity(id = label.id, transactionId = label.transactionId, label = label.label)
        )
}
