package com.smsexpensetracker.core.csv

import com.smsexpensetracker.domain.model.ParseMethod
import com.smsexpensetracker.domain.model.Transaction
import com.smsexpensetracker.domain.model.TransactionType
import java.time.LocalDateTime

object CsvCodec {

    val HEADER: List<String> = listOf(
        "date", "amount", "type", "description", "bankId", "categoryId",
        "smsTimestamp", "parseMethod", "rawSms"
    )

    fun encode(rows: List<List<String>>): String =
        rows.joinToString("\n") { row ->
            row.joinToString(",") { field -> escape(field) }
        }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    row.add(field.toString())
                    field.setLength(0)
                }
                c == '\n' -> {
                    row.add(field.toString())
                    field.setLength(0)
                    rows.add(row.toList())
                    row.clear()
                }
                c == '\r' -> Unit
                else -> field.append(c)
            }
            i++
        }
        if (inQuotes) throw IllegalArgumentException("Unterminated quoted field in CSV")
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row.toList())
        }
        return rows
    }

    fun requireHeader(rows: List<List<String>>) {
        if (rows.isEmpty()) throw IllegalArgumentException("CSV is empty")
        if (rows.first() != HEADER) throw IllegalArgumentException("Unexpected CSV header")
    }

    fun toCsv(transactions: List<Transaction>): String {
        val body = transactions.map { t ->
            listOf(
                t.transactionDate.toString(),
                t.amount.toString(),
                t.transactionType.name,
                t.description,
                t.bankId.toString(),
                t.categoryId?.toString() ?: "",
                t.smsTimestamp.toString(),
                t.parseMethod.name,
                t.rawSms
            )
        }
        return encode(listOf(HEADER) + body)
    }

    fun fromCsv(text: String): List<CsvTransactionRow> {
        val rows = parse(text)
        requireHeader(rows)
        return rows.drop(1).mapNotNull { toTransactionRow(it) }
    }

    fun toTransactionRow(row: List<String>): CsvTransactionRow? {
        if (row.size < HEADER.size) return null
        val amount = row[1].toLongOrNull() ?: return null
        val type = TransactionType.values().firstOrNull { it.name == row[2] } ?: return null
        val date = runCatching { LocalDateTime.parse(row[0]) }.getOrNull() ?: return null
        return CsvTransactionRow(
            amount = amount,
            type = type,
            description = row[3],
            transactionDate = date,
            bankId = row[4].toLongOrNull(),
            categoryId = row[5].toLongOrNull(),
            smsTimestamp = row[6].toLongOrNull() ?: 0L,
            parseMethod = ParseMethod.values().firstOrNull { it.name == row[7] } ?: ParseMethod.SMS,
            rawSms = row[8]
        )
    }
}
