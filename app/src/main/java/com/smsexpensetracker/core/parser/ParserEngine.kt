package com.smsexpensetracker.core.parser

import com.smsexpensetracker.domain.model.TransactionType
import com.smsexpensetracker.domain.value.ParsedResult

object ParserEngine {

    fun parse(
        smsBody: String,
        sender: String,
        rules: List<Pair<Long, String>>
    ): ParsedResult {
        val senderId = SenderDetector.detect(sender)

        for ((bankId, pattern) in rules) {
            val match = RegexParser.parse(smsBody, pattern, bankId) ?: continue
            val type = TypeInferrer.infer(smsBody)
            val confidence = ConfidenceScorer.score(
                smsBody, pattern,
                hasAmount = match.amount > 0L,
                hasDescription = match.description.isNotEmpty()
            )
            return ParsedResult(
                amount = match.amount,
                type = type,
                description = match.description,
                bankId = bankId,
                confidence = confidence.value,
                rawSms = smsBody
            )
        }

        return ParsedResult(
            amount = 0,
            type = TransactionType.DEBIT,
            description = "",
            bankId = null,
            confidence = 0f,
            rawSms = smsBody,
            errorMessage = "No matching rule for sender ${senderId.value}"
        )
    }
}
