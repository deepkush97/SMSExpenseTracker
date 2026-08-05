package com.smsexpensetracker.ui.util

import kotlin.math.absoluteValue

object AmountFormatter {
    fun formatPaisa(paisa: Long): String {
        val abs = paisa.absoluteValue
        val rupees = abs / 100
        val paise = abs % 100
        val grouped = indianGrouping(rupees)
        val sign = if (paisa < 0) "-" else ""
        return "₹$sign$grouped.${paise.toString().padStart(2, '0')}"
    }

    fun formatAmountWithSign(paisa: Long): String {
        val sign = if (paisa < 0) "-" else "+"
        return "$sign${formatPaisa(paisa.absoluteValue)}"
    }

    fun formatPaisaInput(paisa: Long): String {
        val abs = paisa.absoluteValue
        val rupees = abs / 100
        val paise = abs % 100
        return "$rupees.${paise.toString().padStart(2, '0')}"
    }

    private fun indianGrouping(number: Long): String {
        if (number == 0L) return "0"
        val digits = number.toString()
        val len = digits.length
        if (len <= 3) return digits

        val rightmost3 = digits.substring(len - 3)
        var remaining = digits.substring(0, len - 3)
        val groups = mutableListOf(rightmost3)

        while (remaining.isNotEmpty()) {
            val take = remaining.length.coerceAtMost(2)
            groups.add(0, remaining.substring(remaining.length - take))
            remaining = remaining.substring(0, remaining.length - take)
        }

        return groups.joinToString(",")
    }
}

fun formatPaisa(paisa: Long): String = AmountFormatter.formatPaisa(paisa)

fun formatAmountWithSign(paisa: Long): String = AmountFormatter.formatAmountWithSign(paisa)

fun formatPaisaInput(paisa: Long): String = AmountFormatter.formatPaisaInput(paisa)
