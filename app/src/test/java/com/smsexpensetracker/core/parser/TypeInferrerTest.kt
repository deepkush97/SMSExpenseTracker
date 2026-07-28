package com.smsexpensetracker.core.parser


import com.smsexpensetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TypeInferrerTest(
    private val smsBody: String,
    private val expectedType: TransactionType
) {
    companion object {
        @Parameterized.Parameters(name = "{index}: {1}")
        @JvmStatic
        fun data(): Collection<Array<Any>> = listOf(
            // Debit keywords
            arrayOf("ICICI Bank Acct XX123 debited for Rs 242.00", TransactionType.DEBIT),
            arrayOf("Spent Rs.4831.76 On HDFC Bank Card", TransactionType.DEBIT),
            arrayOf("INR 1000.00 deducted from HDFC Bank A/C", TransactionType.DEBIT),
            arrayOf("INR 1403.36 debited DCB Bank a/c", TransactionType.DEBIT),
            arrayOf("Rs. 546.00 spent from Pluxee Meal Card", TransactionType.DEBIT),
            arrayOf("Payment Successful! Rs. 66093.00", TransactionType.DEBIT),
            // Credit keywords
            arrayOf("Rs.12000.00 credited to HDFC Bank A/c", TransactionType.CREDIT),
            arrayOf("INR 1,000.00 deposited in HDFC Bank A/c", TransactionType.CREDIT),
            arrayOf("Alert! Rs. 32 refunded by someComp", TransactionType.CREDIT),
            arrayOf("credited with INR 546.00 as a reversal", TransactionType.CREDIT),
            // Debit takes priority over credit when both present
            arrayOf("Rs.500 debited and Rs.200 credited", TransactionType.DEBIT),
            // No match defaults to DEBIT
            arrayOf("Some random message without keywords", TransactionType.DEBIT),
            // Case insensitivity
            arrayOf("DEBITED from account", TransactionType.DEBIT),
            arrayOf("CREDITED to account", TransactionType.CREDIT)
        )
    }

    @Test
    fun infer() {
        assertEquals(expectedType, TypeInferrer.infer(smsBody))
    }
}