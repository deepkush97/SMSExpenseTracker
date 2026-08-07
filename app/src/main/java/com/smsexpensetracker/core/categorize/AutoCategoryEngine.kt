package com.smsexpensetracker.core.categorize

import com.smsexpensetracker.domain.model.UserCategoryRule

object AutoCategoryEngine {

    fun matchCategory(description: String, rules: List<UserCategoryRule>): Long? {
        val lower = description.lowercase()
        return rules.firstOrNull { lower.contains(it.pattern.lowercase()) }?.categoryId
    }
}
