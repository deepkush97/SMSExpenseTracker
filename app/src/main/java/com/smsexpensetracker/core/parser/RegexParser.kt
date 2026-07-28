package com.smsexpensetracker.core.parser

data class RegexMatch(
    val amount: Long,
    val description: String,
    val bankId: Long,
    val rawSms: String
)

object RegexParser {
    fun parse(smsBody: String, pattern: String, bankId: Long): RegexMatch? {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val matchResult = regex.find(smsBody) ?: return null

        val amountStr = matchResult.groupValues.getOrNull(1) ?: return null
        val description = matchResult.groupValues.getOrNull(2) ?: ""

        val amount = parsePaisa(amountStr) ?: return null

        return RegexMatch(
            amount = amount,
            description = description,
            bankId = bankId,
            rawSms = smsBody
        )

    }

    private fun parsePaisa(amountStr: String): Long? {
        val cleaned = amountStr.replace(
            ",", ""
        )
        val rupees = cleaned.toDoubleOrNull() ?: return null
        return (rupees * 100).toLong()
    }
}