package com.smsexpensetracker.ui.screens.transactions

import com.smsexpensetracker.core.parser.parsePaisa

data class EditFormErrors(
    val amount: String? = null,
    val description: String? = null
)

fun validateTransactionEdit(amountInput: String, description: String): EditFormErrors {
    val amountPaisa = parsePaisa(amountInput)
    return EditFormErrors(
        amount = when {
            amountInput.isBlank() -> "Amount is required"
            amountPaisa == null -> "Enter a valid amount"
            amountPaisa <= 0 -> "Amount must be greater than zero"
            else -> null
        },
        description = when {
            description.isBlank() -> "Description is required"
            description.length > 200 -> "Description must be 200 characters or fewer"
            else -> null
        }
    )
}
