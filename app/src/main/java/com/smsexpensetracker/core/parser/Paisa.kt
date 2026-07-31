package com.smsexpensetracker.core.parser

fun parsePaisa(input: String): Long? {
    val cleaned = input.trim().replace(",", "")
    val match = Regex("^(\\d+)(?:\\.(\\d{1,2}))?$").matchEntire(cleaned) ?: return null
    val rupees = match.groupValues[1].toLongOrNull() ?: return null
    val paise = match.groupValues.getOrNull(2)?.padEnd(2, '0')?.toLongOrNull() ?: 0L
    return rupees * 100 + paise
}
