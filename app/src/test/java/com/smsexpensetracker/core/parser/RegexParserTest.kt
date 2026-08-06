package com.smsexpensetracker.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RegexParserTest(
    private val sms: String,
    private val pattern: String,
    private val bankId: Long,
    private val ruleName: String,
    private val expectedAmount: Long?,
    private val expectedDescription: String?
) {
    companion object {
        @Parameterized.Parameters(name = "{3}")
        @JvmStatic
        fun data(): Collection<Array<Any?>> = listOf(
            arrayOf(
                "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 2468 to 7308080808",
                "Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
                1L, "HDFC CC Debit", 483176L, "Acme Inc."
            ),
            arrayOf(
                "Credit Alert!\nRs.12000.00 credited to HDFC Bank A/c XX1111 on 18-07-26 from VPA yourupi@addr (UPI 656540994008)",
                "Rs\\.([\\d,.]+) credited to HDFC Bank A/c \\w+ on [\\d-]+ from VPA (.+?) \\(UPI",
                1L, "HDFC UPI Credit", 1200000L, "yourupi@addr"
            ),
            arrayOf(
                "PAYMENT ALERT! \nINR 1000.00 deducted from HDFC Bank A/C No 1234 towards Some CORP UMRN: HDFC7011403241000251",
                "INR ([\\d,.]+) deducted from HDFC Bank A/C No \\w+ towards (.+?) UMRN",
                1L, "HDFC e-Mandate", 100000L, "Some CORP"
            ),
            arrayOf(
                "Update! INR 1,000.00 deposited in HDFC Bank A/c XX1233 on 31-MAR-26 for NEFT Cr-ICIC0099999-SOMECOMPANY-someName.Avl bal INR 1,01,000.95. Cheque deposits in A/C are subject to clearing",
                "INR ([\\d,.]+) deposited in HDFC Bank A/c \\w+ on [\\w-]+ for NEFT Cr-(.+?)\\.?Avl bal",
                1L, "HDFC NEFT Credit", 100000L, "ICIC0099999-SOMECOMPANY-someName"
            ),
            arrayOf(
                "ICICI Bank Acct XX123 debited for Rs 242.00 on 26-Jul-26; BUS Ticket credited. UPI:003637672623. Call 18002662 for dispute. SMS BLOCK 796 to 9215676766.",
                "ICICI Bank Acct \\w+ debited for Rs ([\\d,.]+) on [\\w-]+; (.+?) credited\\. UPI",
                2L, "ICICI UPI Debit", 24200L, "BUS Ticket"
            ),
            arrayOf(
                "Dear Customer, Acct XX123 is credited with Rs 20.00 on 19-Jul-26 from NPCI BHIM. UPI:103691213332-ICICI Bank.",
                "Acct \\w+ is credited with Rs ([\\d,.]+) on [\\w-]+ from (.+?)\\. UPI",
                2L, "ICICI UPI Credit", 2000L, "NPCI BHIM"
            ),
            arrayOf(
                "Txn Rs.25.00\nOn HDFC Bank Card 1111\nAt Q123456789@ybl \nby UPI 620436716168\nOn 23-07\nNot You?\nCall 18002586161/SMS BLOCK CC 2468 to 7308080808",
                "Txn Rs\\.([\\d,.]+)[\\s\\S]*?On HDFC Bank Card \\d{4}[\\s\\S]*?At (.+?)\\s+by UPI \\d+[\\s\\S]*?On [\\d-]+",
                1L, "HDFC CC UPI Debit", 2500L, "Q123456789@ybl"
            ),
            arrayOf(
                "Alert! Rs. 32 refunded by someComp on 20/JUL/2026 & adjusted against HDFC Bank Credit Card 1111 View updated balance here: bank link",
                "Alert! Rs\\.? ([\\d,.]+) refunded by (.+?) on [\\d/]+.*?HDFC Bank Credit Card \\d{4}",
                1L, "HDFC CC Refund", 3200L, "someComp"
            ),
            arrayOf(
                "Payment Successful! Rs. 66093.00 from A/c **********1233 to SOMECORP via HDFC Bank NetBanking. Not you?Call 18002586161",
                "Rs\\.? ([\\d,.]+) from A/c [\\w*]+ to (.+?) via HDFC Bank NetBanking",
                1L, "HDFC NetBanking", 6609300L, "SOMECORP"
            ),
            arrayOf(
                "ICICI Bank Account XX123 is credited with Rs 61,000.00 on 03-Jul-26 by Account linked to mobile number XXXXX01234. IMPS Ref. no. 618441385660.",
                "ICICI Bank Account \\w+ is credited with Rs ([\\d,.]+) on [\\w-]+ by (.+?)\\. IMPS",
                2L, "ICICI IMPS Credit", 6100000L, "Account linked to mobile number XXXXX01234"
            ),
            arrayOf(
                "INR 1403.36 debited DCB Bank a/c*1234 POS/Ecom txn to cafe de lar on 19-06-2026 07:59 PM.Not you?Call 0226899777 or SMS BLOCKCARD <Last 4 digits>to 9821878789",
                "INR ([\\d,.]+) debited DCB Bank a/c\\*\\d+ POS/Ecom txn to (.+?) on [\\d-]+ \\d{2}:\\d{2} [AP]M",
                3L, "DCB POS/Ecom Debit", 140336L, "cafe de lar"
            ),
            arrayOf(
                "Rs. 546.00 spent from Pluxee  Meal Card wallet, card no.xx4910 on 28-06-2026 21:38:47 at SWIGGY . Avl bal Rs.9388.14. Not you call 18002106919",
                "Rs\\. ([\\d,.]+) spent from Pluxee\\s+Meal Card wallet, card no\\.\\w+ on [\\d-]+ \\d{2}:\\d{2}:\\d{2} at (.+?) \\. Avl bal",
                4L, "Pluxee Meal Spend", 54600L, "SWIGGY"
            ),
            arrayOf(
                "Your Pluxee Card xx4910 has been credited with INR 546.00 on Sun Jun 28 2026 22:41:31as a reversal against a previous transaction on Jun 28,2026 21:38:47.",
                "Your Pluxee Card \\w+ has been credited with INR ([\\d,.]+) on (.+?)as a reversal",
                4L, "Pluxee Reversal", 54600L, "Sun Jun 28 2026 22:41:31"
            ),
            arrayOf(
                "Your Pluxee Card has been successfully credited with Rs.2200 towards  Meal Wallet on Thu Sep 05 2024 17:03:06. Your current Meal Wallet balance is Rs.7142.70.",
                "credited with Rs\\.([\\d,.]+) towards\\s+Meal Wallet on (.+?)\\. Your",
                4L, "Pluxee Wallet Load", 220000L, "Thu Sep 05 2024 17:03:06"
            ),
            arrayOf(
                "This is not a bank SMS",
                "Spent Rs\\.([\\d,.]+) On HDFC",
                1L, "No match", null, null
            ),
            arrayOf(
                "Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 2468 to 7308080808",
                "Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}",
                1L, "HDFC CC Debit TPL", 483176L, "Acme Inc."
            ),
            arrayOf(
                "Credit Alert!\nRs.12000.00 credited to HDFC Bank A/c XX1111 on 18-07-26 from VPA yourupi@addr (UPI 656540994008)",
                "Rs.{amount} credited to HDFC Bank A/c {account} on {date} from VPA {description} (UPI",
                1L, "HDFC UPI Credit TPL", 1200000L, "yourupi@addr"
            ),
            arrayOf(
                "PAYMENT ALERT! \nINR 1000.00 deducted from HDFC Bank A/C No 1234 towards Some CORP UMRN: HDFC7011403241000251",
                "INR {amount} deducted from HDFC Bank A/C No {account} towards {description} UMRN",
                1L, "HDFC e-Mandate TPL", 100000L, "Some CORP"
            ),
            arrayOf(
                "Update! INR 1,000.00 deposited in HDFC Bank A/c XX1233 on 31-MAR-26 for NEFT Cr-ICIC0099999-SOMECOMPANY-someName.Avl bal INR 1,01,000.95. Cheque deposits in A/C are subject to clearing",
                "INR {amount} deposited in HDFC Bank A/c {account} on {date} for NEFT Cr-{description}.Avl bal",
                1L, "HDFC NEFT Credit TPL", 100000L, "ICIC0099999-SOMECOMPANY-someName"
            ),
            arrayOf(
                "ICICI Bank Acct XX123 debited for Rs 242.00 on 26-Jul-26; BUS Ticket credited. UPI:003637672623. Call 18002662 for dispute. SMS BLOCK 796 to 9215676766.",
                "ICICI Bank Acct {account} debited for Rs {amount} on {date}; {description} credited. UPI",
                2L, "ICICI UPI Debit TPL", 24200L, "BUS Ticket"
            ),
            arrayOf(
                "Dear Customer, Acct XX123 is credited with Rs 20.00 on 19-Jul-26 from NPCI BHIM. UPI:103691213332-ICICI Bank.",
                "Acct {account} is credited with Rs {amount} on {date} from {description}. UPI",
                2L, "ICICI UPI Credit TPL", 2000L, "NPCI BHIM"
            ),
            arrayOf(
                "ICICI Bank Account XX123 is credited with Rs 61,000.00 on 03-Jul-26 by Account linked to mobile number XXXXX01234. IMPS Ref. no. 618441385660.",
                "ICICI Bank Account {account} is credited with Rs {amount} on {date} by {description}. IMPS",
                2L, "ICICI IMPS Credit TPL", 6100000L, "Account linked to mobile number XXXXX01234"
            ),
            arrayOf(
                "INR 1403.36 debited DCB Bank a/c*1234 POS/Ecom txn to cafe de lar on 19-06-2026 07:59 PM.Not you?Call 0226899777 or SMS BLOCKCARD <Last 4 digits>to 9821878789",
                "INR {amount} debited DCB Bank a/c*{card} POS/Ecom txn to {description} on {date}",
                3L, "DCB POS/Ecom Debit TPL", 140336L, "cafe de lar"
            ),
            arrayOf(
                "Rs. 546.00 spent from Pluxee  Meal Card wallet, card no.xx4910 on 28-06-2026 21:38:47 at SWIGGY . Avl bal Rs.9388.14. Not you call 18002106919",
                "Rs. {amount} spent from Pluxee Meal Card wallet, card no.{card} on {date} at {description}. Avl bal",
                4L, "Pluxee Meal Spend TPL", 54600L, "SWIGGY"
            ),
            arrayOf(
                "Your Pluxee Card xx4910 has been credited with INR 546.00 on Sun Jun 28 2026 22:41:31as a reversal against a previous transaction on Jun 28,2026 21:38:47.",
                "Your Pluxee Card xx{card} has been credited with INR {amount} on {date}as {description}.",
                4L, "Pluxee Reversal TPL", 54600L, "a reversal against a previous transaction on Jun 28,2026 21:38:47"
            ),
            arrayOf(
                "Your Pluxee Card has been successfully credited with Rs.2200 towards  Meal Wallet on Thu Sep 05 2024 17:03:06. Your current Meal Wallet balance is Rs.7142.70.",
                "credited with Rs.{amount} towards{wallet} on {description}. Your",
                4L, "Pluxee Wallet Load TPL", 220000L, "Thu Sep 05 2024 17:03:06"
            ),
            arrayOf(
                "Txn Rs.25.00\nOn HDFC Bank Card 1111\nAt Q123456789@ybl \nby UPI 620436716168\nOn 23-07\nNot You?\nCall 18002586161/SMS BLOCK CC 2468 to 7308080808",
                "Txn Rs.{amount} On HDFC Bank Card {card} At {description} by UPI {upi} On {date}",
                1L, "HDFC CC UPI Debit TPL", 2500L, "Q123456789@ybl"
            ),
            arrayOf(
                "Alert! Rs. 32 refunded by someComp on 20/JUL/2026 & adjusted against HDFC Bank Credit Card 1111 View updated balance here: bank link",
                "Alert! Rs. {amount} refunded by {description} on {date} & adjusted against HDFC Bank Credit Card {card}",
                1L, "HDFC CC Refund TPL", 3200L, "someComp"
            ),
            arrayOf(
                "Payment Successful! Rs. 66093.00 from A/c **********1233 to SOMECORP via HDFC Bank NetBanking. Not you?Call 18002586161",
                "Rs. {amount} from A/c {account} to {description} via HDFC Bank NetBanking",
                1L, "HDFC NetBanking TPL", 6609300L, "SOMECORP"
            )
        )
    }

    @Test
    fun parse() {
        val result = RegexParser.parse(sms, pattern, bankId)
        if (expectedAmount == null) {
            assertNull(result)
        } else {
            assertEquals(expectedAmount, result!!.amount)
            assertEquals(expectedDescription, result.description)
            assertEquals(bankId, result.bankId)
        }
    }
}
