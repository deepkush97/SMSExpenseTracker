package com.smsexpensetracker.domain.model

data class Bank(
    val id: Long,
    val name: String,
    val smsSender: String
)