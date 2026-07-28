package com.smsexpensetracker.core.parser

import com.smsexpensetracker.domain.model.TransactionType

object TypeInferrer {
    private val creditKeywords = listOf<String>(
        "credited",
        "credit",
        "deposited",
        "refunded",
        "reversal",
        "refund",
        "wallet.*credited",
        "repayment"
    )

    private val debitKeywords = listOf<String>(
        "debited",
        "debit",
        "spent",
        "deducted",
        "payment",
        "withdrawal",
        "purchase",
        "spent from",
        "paid"
    )

    fun infer(smsBody: String): TransactionType {
        val lower = smsBody.lowercase()
        for (keyword in debitKeywords) {
            if (Regex(keyword).containsMatchIn(lower)) return TransactionType.DEBIT
        }
        for (keyword in creditKeywords) {
            if (Regex(keyword).containsMatchIn(lower)) return TransactionType.CREDIT
        }

        return TransactionType.DEBIT

    }
}