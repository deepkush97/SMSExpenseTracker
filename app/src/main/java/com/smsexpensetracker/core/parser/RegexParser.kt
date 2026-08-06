package com.smsexpensetracker.core.parser

data class RegexMatch(
    val amount: Long,
    val description: String,
    val bankId: Long,
    val rawSms: String
)

private val WHITESPACE_RUN = Regex("\\s+")

internal fun collapseWhitespace(text: String): String = text.trim().replace(WHITESPACE_RUN, " ")

object RegexParser {
    fun parse(smsBody: String, pattern: String, bankId: Long): RegexMatch? {
        val body = collapseWhitespace(smsBody)
        if (TemplateCompiler.isTemplate(pattern)) {
            return TemplateCompiler.extract(body, pattern, bankId)?.copy(rawSms = smsBody)
        }
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val matchResult = regex.find(body) ?: return null

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
}