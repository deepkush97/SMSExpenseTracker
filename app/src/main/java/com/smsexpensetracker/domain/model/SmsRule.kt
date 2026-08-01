package com.smsexpensetracker.domain.model

data class SmsRule(
    val id: Long,
    val bankId: Long,
    val pattern: String,
    val description: String,
    val isActive: Boolean = true
)