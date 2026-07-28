package com.smsexpensetracker.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = BankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bankId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BankEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),

    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bankId: Long,
    val amount: Long, // In units
    val type: TransactionType,
    val description: String,
    val transactionDate: LocalDateTime,
    val categoryId: Long? = null,
    val rawSms: String,
    val smsTimestamp: Long,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class TransactionType {
    CREDIT, DEBIT
}