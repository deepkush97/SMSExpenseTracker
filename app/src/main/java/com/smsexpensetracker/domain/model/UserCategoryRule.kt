package com.smsexpensetracker.domain.model

data class UserCategoryRule(
    val id: Long,
    val pattern: String,
    val categoryId: Long
)