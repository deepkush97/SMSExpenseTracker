package com.smsexpensetracker.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParserEngineTest(
    private val smsBody: String,
    private val sender: String,
    private val rules: List<Pair<Long, String>>,
    private val expectedAmount: Long?,
    private val expectedType: String?,
    private val expectedDescription: String?,
) {
    companion object {
        private val hdfcRules = listOf(
            1L to "Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
            1L to "Rs\\.([\\d,.]+) credited to HDFC Bank A/c \\w+ on [\\d-]+ from VPA (.+?) \\(UPI",
            1L to "INR ([\\d,.]+) deducted from HDFC Bank A/C No \\w+ towards (.+?) UMRN",
            1L to "INR ([\\d,.]+) deposited in HDFC Bank A/c \\w+ on [\\w-]+ for NEFT Cr-(.+?)\\.?Avl bal",
            1L to "Txn Rs\\.([\\d,.]+)[\\s\\S]*?On HDFC Bank Card \\d{4}[\\s\\S]*?At (.+?)\\n[\\s\\S]*?by UPI \\d+[\\s\\S]*?On [\\d-]+",
            1L to "Alert! Rs\\.? ([\\d,.]+) refunded by (.+?) on [\\d/]+.*?HDFC Bank Credit Card \\d{4}",
            1L to "Rs\\.? ([\\d,.]+) from A/c [\\w*]+ to (.+?) via HDFC Bank NetBanking"
        )

        private val iciciRules = listOf(
            2L to "ICICI Bank Acct \\w+ debited for Rs ([\\d,.]+) on [\\w-]+; (.+?) credited\\. UPI",
            2L to "Acct \\w+ is credited with Rs ([\\d,.]+) on [\\w-]+ from (.+?)\\. UPI",
            2L to "ICICI Bank Account \\w+ is credited with Rs ([\\d,.]+) on [\\w-]+ by (.+?)\\. IMPS"
        )

        private val dcbRules = listOf(
            3L to "INR ([\\d,.]+) debited DCB Bank a/c\\*\\d+ POS/Ecom txn to (.+?) on [\\d-]+ \\d{2}:\\d{2} [AP]M"
        )

        private val pluxeeRules = listOf(
            4L to "Rs\\. ([\\d,.]+) spent from Pluxee\\s+Meal Card wallet, card no\\.\\w+ on [\\d-]+ \\d{2}:\\d{2}:\\d{2} at (.+?) \\. Avl bal",
            4L to "Your Pluxee Card \\w+ has been credited with INR ([\\d,.]+) on (.+?)as a reversal",
            4L to "credited with Rs\\.([\\d,.]+) towards\\s+Meal Wallet on (.+?)\\. Your"
        )

        @Parameterized.Parameters(name = "{1}")
        @JvmStatic
        fun data(): Collection<Array<Any?>> = listOf(
            arrayOf(
                "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 2468 to 7308080808",
                "AD-HDFCBK-S",
                hdfcRules,
                483176L,
                "DEBIT",
                "Acme Inc."
            ),
            arrayOf(
                "Txn Rs.25.00\nOn HDFC Bank Card 1111\nAt Q123456789@ybl \nby UPI 620436716168\nOn 23-07\nNot You?\nCall 18002586161/SMS BLOCK CC 2468 to 7308080808",
                "AD-HDFCBK-S",
                hdfcRules,
                2500L,
                "DEBIT",
                "Q123456789@ybl "
            ),
            arrayOf(
                "Alert! Rs. 32 refunded by someComp on 20/JUL/2026 & adjusted against HDFC Bank Credit Card 1111 View updated balance here: bank link",
                "AD-HDFCBK-S",
                hdfcRules,
                3200L,
                "CREDIT",
                "someComp"
            ),
            arrayOf(
                "Credit Alert!\nRs.12000.00 credited to HDFC Bank A/c XX1111 on 18-07-26 from VPA yourupi@addr (UPI 656540994008)",
                "AD-HDFCBK-S",
                hdfcRules,
                1200000L,
                "CREDIT",
                "yourupi@addr"
            ),
            arrayOf(
                "PAYMENT ALERT! \nINR 1000.00 deducted from HDFC Bank A/C No 1234 towards Some CORP UMRN: HDFC7011403241000251",
                "AD-HDFCBK-S",
                hdfcRules,
                100000L,
                "DEBIT",
                "Some CORP"
            ),
            arrayOf(
                "Payment Successful! Rs. 66093.00 from A/c **********1233 to SOMECORP via HDFC Bank NetBanking. Not you?Call 18002586161",
                "AD-HDFCBK-S",
                hdfcRules,
                6609300L,
                "DEBIT",
                "SOMECORP"
            ),
            arrayOf(
                "Update! INR 1,000.00 deposited in HDFC Bank A/c XX1233 on 31-MAR-26 for NEFT Cr-ICIC0099999-SOMECOMPANY-someName.Avl bal INR 1,01,000.95. Cheque deposits in A/C are subject to clearing",
                "AD-HDFCBK-S",
                hdfcRules,
                100000L,
                "CREDIT",
                "ICIC0099999-SOMECOMPANY-someName"
            ),
            arrayOf(
                "ICICI Bank Acct XX123 debited for Rs 242.00 on 26-Jul-26; BUS Ticket credited. UPI:003637672623. Call 18002662 for dispute. SMS BLOCK 796 to 9215676766.",
                "AD-ICICIT-S",
                iciciRules,
                24200L,
                "DEBIT",
                "BUS Ticket"
            ),
            arrayOf(
                "Dear Customer, Acct XX123 is credited with Rs 20.00 on 19-Jul-26 from NPCI BHIM. UPI:103691213332-ICICI Bank.",
                "AD-ICICIT-S",
                iciciRules,
                2000L,
                "CREDIT",
                "NPCI BHIM"
            ),
            arrayOf(
                "ICICI Bank Account XX123 is credited with Rs 61,000.00 on 03-Jul-26 by Account linked to mobile number XXXXX01234. IMPS Ref. no. 618441385660.",
                "AD-ICICIT-S",
                iciciRules,
                6100000L,
                "CREDIT",
                "Account linked to mobile number XXXXX01234"
            ),
            arrayOf(
                "INR 1403.36 debited DCB Bank a/c*1234 POS/Ecom txn to cafe de lar on 19-06-2026 07:59 PM.Not you?Call 0226899777 or SMS BLOCKCARD <Last 4 digits>to 9821878789",
                "JD-DCBANK-T",
                dcbRules,
                140336L,
                "DEBIT",
                "cafe de lar"
            ),
            arrayOf(
                "Rs. 546.00 spent from Pluxee  Meal Card wallet, card no.xx4910 on 28-06-2026 21:38:47 at SWIGGY . Avl bal Rs.9388.14. Not you call 18002106919",
                "VD-Pluxee-S",
                pluxeeRules,
                54600L,
                "DEBIT",
                "SWIGGY"
            ),
            arrayOf(
                "Your Pluxee Card xx4910 has been credited with INR 546.00 on Sun Jun 28 2026 22:41:31as a reversal against a previous transaction on Jun 28,2026 21:38:47.",
                "VD-Pluxee-S",
                pluxeeRules,
                54600L,
                "CREDIT",
                "Sun Jun 28 2026 22:41:31"
            ),
            arrayOf(
                "Your Pluxee Card has been successfully credited with Rs.2200 towards  Meal Wallet on Thu Sep 05 2024 17:03:06. Your current Meal Wallet balance is Rs.7142.70.",
                "VD-Pluxee-S",
                pluxeeRules,
                220000L,
                "CREDIT",
                "Thu Sep 05 2024 17:03:06"
            ),
            arrayOf(
                "This is not a bank SMS",
                "UNKNOWN-SENDER",
                emptyList<Pair<Long, String>>(),
                null,
                null,
                null
            )
        )
    }

    private val engine = ParserEngine

    @Test
    fun parse() {
        val result = engine.parse(smsBody, sender, rules)
        if (expectedAmount == null) {
            assertNotNull(result.errorMessage)
            assertEquals(0f, result.confidence, 0.001f)
        } else {
            assertEquals(expectedAmount, result.amount)
            assertEquals(expectedType, result.type.name)
            assertEquals(expectedDescription, result.description)
            assertEquals(1.0f, result.confidence)
        }
    }
}