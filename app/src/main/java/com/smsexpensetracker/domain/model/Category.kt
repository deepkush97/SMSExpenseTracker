package com.smsexpensetracker.domain.model

data class Category(
    val id: Long,
    val name: String,
    val icon: String,
    val color: Int,
    val isDefault: Boolean
)