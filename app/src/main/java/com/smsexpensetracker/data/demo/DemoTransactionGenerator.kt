package com.smsexpensetracker.data.demo

import com.smsexpensetracker.core.database.entity.ParseMethod
import com.smsexpensetracker.core.database.entity.TransactionEntity
import com.smsexpensetracker.core.database.entity.TransactionType
import java.time.LocalDateTime

private data class DemoItem(
    val description: String,
    val bankId: Long,
    val categoryId: Long,
    val type: TransactionType,
    val amountPaisa: Long
)

object DemoTransactionGenerator {

    fun generate(): List<TransactionEntity> {
        val items = listOf(
            DemoItem("Salary - ACME Corp", 1, 12, TransactionType.CREDIT, 85_000_00),
            DemoItem("SIP - Mutual Fund", 1, 13, TransactionType.DEBIT, 10_000_00),
            DemoItem("Rent - Green Park", 1, 10, TransactionType.DEBIT, 22_000_00),
            DemoItem("BigBasket", 2, 2, TransactionType.DEBIT, 2_450_50),
            DemoItem("Blinkit", 2, 2, TransactionType.DEBIT, 780_00),
            DemoItem("Zomato", 1, 1, TransactionType.DEBIT, 460_00),
            DemoItem("Swiggy", 2, 1, TransactionType.DEBIT, 320_75),
            DemoItem("Pluxee Lunch", 5, 1, TransactionType.DEBIT, 180_00),
            DemoItem("Indian Oil", 3, 3, TransactionType.DEBIT, 2_000_00),
            DemoItem("Reliance Jio Recharge", 4, 4, TransactionType.DEBIT, 299_00),
            DemoItem("Amazon", 3, 5, TransactionType.DEBIT, 1_299_00),
            DemoItem("Refund - Flipkart", 3, 5, TransactionType.CREDIT, 950_00),
            DemoItem("Netflix", 1, 6, TransactionType.DEBIT, 649_00),
            DemoItem("Movie Tickets", 4, 6, TransactionType.DEBIT, 600_00),
            DemoItem("1mg", 5, 7, TransactionType.DEBIT, 540_00),
            DemoItem("Uber", 2, 8, TransactionType.DEBIT, 285_00),
            DemoItem("Metro Card Top-up", 3, 8, TransactionType.DEBIT, 500_00),
            DemoItem("Udemy Course", 2, 9, TransactionType.DEBIT, 1_200_00),
            DemoItem("Flight - IndiGo", 4, 11, TransactionType.DEBIT, 5_400_00),
            DemoItem("Misc Expenses", 3, 14, TransactionType.DEBIT, 350_00)
        )
        val now = LocalDateTime.now()
        return buildList {
            for (monthAgo in 2 downTo 0) {
                val monthBase = now.minusMonths(monthAgo.toLong())
                items.forEachIndexed { index, item ->
                    add(
                        TransactionEntity(
                            bankId = item.bankId,
                            amount = item.amountPaisa,
                            type = item.type,
                            description = item.description,
                            transactionDate = monthBase
                                .minusDays(index % 7L)
                                .withHour(12 + index % 8)
                                .withMinute((index * 13) % 60),
                            categoryId = item.categoryId,
                            rawSms = "",
                            smsTimestamp = 0,
                            parseMethod = ParseMethod.MANUAL
                        )
                    )
                }
            }
        }
    }
}
