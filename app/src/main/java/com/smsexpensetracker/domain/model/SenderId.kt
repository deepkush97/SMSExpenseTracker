package com.smsexpensetracker.domain.model

data class SenderId(val value: String) {
    fun extractBankCode(): String {
        val parts = value.split("-")
        return parts.firstOrNull { it.length >= 5 } ?: value
    }
}