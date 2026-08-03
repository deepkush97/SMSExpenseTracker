package com.smsexpensetracker.ui.util

import com.smsexpensetracker.core.parser.TemplateCompiler
import com.smsexpensetracker.domain.model.Bank
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

fun validateBankName(name: String, existing: List<Bank>, editingId: Long?): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "Name is required"
    if (trimmed.length > 30) return "Name must be 30 characters or fewer"
    val duplicate = existing.any { it.id != editingId && it.name.equals(trimmed, ignoreCase = true) }
    if (duplicate) return "A bank with this name already exists"
    return null
}

fun validateBankSender(sender: String): String? {
    if (sender.trim().isEmpty()) return "Sender is required"
    return null
}

fun validateRuleDescription(description: String): String? {
    val trimmed = description.trim()
    if (trimmed.isEmpty()) return "Description is required"
    if (trimmed.length > 60) return "Description must be 60 characters or fewer"
    return null
}

fun validatePattern(pattern: String): String? {
    val trimmed = pattern.trim()
    if (trimmed.isEmpty()) return "Pattern is required"
    if (TemplateCompiler.isTemplate(trimmed)) {
        if (trimmed.count { it == '{' } != trimmed.count { it == '}' }) {
            return "Pattern has unbalanced braces"
        }
        if ("{}" in trimmed) {
            return "Pattern contains an empty {} placeholder"
        }
        val names = TemplateCompiler.findPlaceholders(trimmed)
        var rest = trimmed
        names.forEach { rest = rest.replace("{$it}", "") }
        if ('{' in rest || '}' in rest) {
            return "Placeholder names may only contain letters and digits, and must start with a letter"
        }
        if (names.none { it == "amount" }) {
            return "Pattern must include an {amount} placeholder"
        }
        return null
    }
    return try {
        Pattern.compile(trimmed)
        null
    } catch (e: PatternSyntaxException) {
        "Pattern must be a valid regular expression"
    }
}
