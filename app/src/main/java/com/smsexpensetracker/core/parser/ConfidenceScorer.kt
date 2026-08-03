package com.smsexpensetracker.core.parser

import com.smsexpensetracker.domain.value.ConfidenceScore

object ConfidenceScorer {
    fun score(
        smsBody: String,
        pattern: String,
        hasAmount: Boolean,
        hasDescription: Boolean
    ): ConfidenceScore {
        var score = 0.0f

        if (hasAmount) score += 0.4f
        if (hasDescription) score += 0.2f

        val compiled = if (TemplateCompiler.isTemplate(pattern)) {
            TemplateCompiler.compile(pattern)
        } else {
            Regex(pattern, RegexOption.IGNORE_CASE)
        }
        val match = compiled?.find(smsBody)
        if (match != null) {
            score += 0.3f
            val groups = match.groupValues.size - 1
            score += minOf(groups * 0.05f, 0.1f)
        }

        return ConfidenceScore(minOf(score, 1.0f))
    }
}