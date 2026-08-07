package com.smsexpensetracker.core.categorize

import com.smsexpensetracker.domain.model.Transaction

data class RuleSuggestion(
    val keyword: String,
    val transactionCount: Int,
    val suggestedCategoryId: Long?
)

object RuleSuggestionEngine {

    fun suggest(
        uncategorized: List<Transaction>,
        classified: List<Transaction>,
        minCount: Int = 3,
        minKeywordLength: Int = 3
    ): List<RuleSuggestion> {
        val allByKeyword = HashMap<String, MutableList<Transaction>>()
        fun add(tx: Transaction) {
            tokens(tx.description, minKeywordLength).forEach { kw ->
                allByKeyword.getOrPut(kw) { mutableListOf() }.add(tx)
            }
        }
        uncategorized.forEach(::add)
        classified.forEach(::add)

        return allByKeyword
            .filter { it.value.size >= minCount }
            .filterKeys { it.length >= minKeywordLength }
            .map { (kw, txns) ->
                val evidence = txns.filter { it.categoryId != null }
                val byCategory = evidence.groupingBy { it.categoryId }.eachCount()
                val topCount = byCategory.values.maxOrNull()
                val winners = byCategory.filterValues { it == topCount }
                val categoryId = if (topCount != null && winners.size == 1) {
                    winners.keys.single()
                } else null
                RuleSuggestion(keyword = kw, transactionCount = txns.size, suggestedCategoryId = categoryId)
            }
            .sortedByDescending { it.transactionCount }
    }

    private fun tokens(description: String, minLength: Int): Set<String> =
        description.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(" ")
            .filter { it.length >= minLength }
            .toSet()
}