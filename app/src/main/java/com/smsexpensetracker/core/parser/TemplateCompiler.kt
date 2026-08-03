package com.smsexpensetracker.core.parser

import java.util.regex.PatternSyntaxException

object TemplateCompiler {

    private val PLACEHOLDER = Regex("\\{([a-zA-Z][a-zA-Z0-9]*)\\}")

    fun isTemplate(pattern: String): Boolean = PLACEHOLDER.containsMatchIn(pattern)

    fun findPlaceholders(template: String): List<String> =
        PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toList()

    fun compile(template: String): Regex? {
        val trimmed = template.trim()
        if (trimmed.count { it == '{' } != trimmed.count { it == '}' }) return null
        val placeholders = PLACEHOLDER.findAll(trimmed).toList()
        if (placeholders.isEmpty()) return null
        val names = placeholders.map { it.groupValues[1] }
        if ("amount" !in names) return null

        val builder = StringBuilder()
        var last = 0
        var descriptionCount = 0
        var anchorCount = 0
        var amountSeen = false

        placeholders.forEachIndexed { index, match ->
            val name = match.groupValues[1]
            appendLiteral(builder, trimmed.substring(last, match.range.first))
            val isTerminal = index == placeholders.lastIndex
            val group = when {
                name == "amount" && !amountSeen -> {
                    amountSeen = true
                    "(?<amount>[\\d,]+(?:\\.[\\d]{1,2})?)"
                }
                name == "amount" -> anchorGroup(++anchorCount, isTerminal)
                name == "description" -> {
                    descriptionCount++
                    val groupName = if (descriptionCount == 1) "description" else "description$descriptionCount"
                    if (isTerminal) "(?<$groupName>.+)" else "(?<$groupName>.+?)"
                }
                else -> anchorGroup(++anchorCount, isTerminal)
            }
            builder.append(group)
            last = match.range.last + 1
        }
        appendLiteral(builder, trimmed.substring(last))

        return try {
            Regex(builder.toString(), RegexOption.IGNORE_CASE)
        } catch (e: PatternSyntaxException) {
            null
        }
    }

    fun extract(smsBody: String, template: String, bankId: Long): RegexMatch? {
        val compiled = compile(template) ?: return null
        val match = compiled.find(smsBody) ?: return null
        val amount = match.groups["amount"]?.value?.let { parsePaisa(it) } ?: return null
        val descriptionCount = findPlaceholders(template).count { it == "description" }
        val descriptions = (1..descriptionCount).mapNotNull { i ->
            val name = if (i == 1) "description" else "description$i"
            match.groups[name]?.value?.trim()
        }
        return RegexMatch(
            amount = amount,
            description = descriptions.joinToString("; "),
            bankId = bankId,
            rawSms = smsBody
        )
    }

    private fun anchorGroup(count: Int, isTerminal: Boolean): String =
        if (isTerminal) "(?<a$count>.+)" else "(?<a$count>.+?)"

    private fun appendLiteral(builder: StringBuilder, literal: String) {
        var i = 0
        while (i < literal.length) {
            val c = literal[i]
            if (c.isWhitespace()) {
                while (i < literal.length && literal[i].isWhitespace()) i++
                builder.append("\\s+")
            } else {
                builder.append(Regex.escape(c.toString()))
                i++
            }
        }
    }
}
