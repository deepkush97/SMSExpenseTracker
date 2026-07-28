package com.smsexpensetracker.domain.value

data class ConfidenceScore(val value: Float) {
    init {
        require(value in 0.0f..1.0f) { "Confidence must be 0.0..1.0" }
    }
}