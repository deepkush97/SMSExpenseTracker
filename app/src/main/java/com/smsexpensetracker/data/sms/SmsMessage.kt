package com.smsexpensetracker.data.sms

data class SmsMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val timestamp: Long
)
