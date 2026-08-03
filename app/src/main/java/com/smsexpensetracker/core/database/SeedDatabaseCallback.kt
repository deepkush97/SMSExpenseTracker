package com.smsexpensetracker.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

class SeedDatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedBanks(db)
        seedCategories(db)
        seedSmsRules(db)

    }

    private fun seedBanks(db: SupportSQLiteDatabase) {

        val banks = listOf(
            "HDFC Bank" to "HDFCBK",
            "ICICI Bank" to "ICICIB",
            "State Bank of India" to "SBI",
            "Axis Bank" to "AXISB",
            "Pluxee" to "PLXEE",
            "DCB Bank" to "DCBANK"
        )
        banks.forEachIndexed { index, (name, sender) ->
            db.execSQL(
                "INSERT INTO banks (id, name, smsSender) VALUES (${index + 1}, '$name', '$sender' )"
            )

        }


    }

    private fun seedCategories(db: SupportSQLiteDatabase) {

        val categories = listOf(
            "Food & Dining" to ("restaurant" to -13108),         // teal
            "Groceries" to ("shopping_cart" to -13956304),       // green
            "Fuel" to ("local_gas_station" to -48060),           // orange
            "Bills & Utilities" to ("receipt" to -13676760),     // red
            "Shopping" to ("shopping_bag" to -10496),            // pink
            "Entertainment" to ("movie" to -16581634),           // purple
            "Healthcare" to ("local_hospital" to -14513374),     // red-ish
            "Transportation" to ("directions_car" to -12664161), // blue-grey
            "Education" to ("school" to -4880347),               // blue
            "Rent" to ("home" to -7084816),                      // brown
            "Travel" to ("flight" to -13676760),                 // red
            "Salary" to ("payments" to -13956304),               // green (income)
            "Investments" to ("trending_up" to -16581634),       // purple
            "Other" to ("category" to -7829368)                  // grey (default)
        )

        categories.forEachIndexed { index, record ->
            val (name, iconColor) = record
            val (icon, color) = iconColor
            val isDefault = 1

            db.execSQL(
                "INSERT INTO categories (id, name, icon, color, isDefault) VALUES (${index + 1}, '$name','$icon', $color, $isDefault)"
            )
        }
    }

    private fun seedSmsRules(db: SupportSQLiteDatabase) {

        val rules = listOf(
            "HDFC CC Debit" to 1L to "Spent Rs.{amount} On HDFC Bank Card {card} At {description} On {date}",
            "HDFC CC UPI Debit" to 1L to "Txn Rs.{amount} On HDFC Bank Card {card} At {description} by UPI {upi} On {date}",
            "HDFC CC Refund" to 1L to "Alert! Rs. {amount} refunded by {description} on {date} & adjusted against HDFC Bank Credit Card {card}",
            "HDFC UPI Credit" to 1L to "Rs.{amount} credited to HDFC Bank A/c {account} on {date} from VPA {description} (UPI",
            "HDFC e-Mandate" to 1L to "INR {amount} deducted from HDFC Bank A/C No {account} towards {description} UMRN",
            "HDFC NetBanking" to 1L to "Rs. {amount} from A/c {account} to {description} via HDFC Bank NetBanking",
            "HDFC NEFT Credit" to 1L to "INR {amount} deposited in HDFC Bank A/c {account} on {date} for NEFT Cr-{description}.Avl bal",
            "ICICI UPI Debit" to 2L to "ICICI Bank Acct {account} debited for Rs {amount} on {date}; {description} credited. UPI",
            "ICICI UPI Credit" to 2L to "Acct {account} is credited with Rs {amount} on {date} from {description}. UPI",
            "ICICI IMPS Credit" to 2L to "ICICI Bank Account {account} is credited with Rs {amount} on {date} by {description}. IMPS",
            "Pluxee Meal Spend" to 5L to "Rs. {amount} spent from Pluxee Meal Card wallet, card no.{card} on {date} at {description}. Avl bal",
            "Pluxee Reversal" to 5L to "Your Pluxee Card xx{card} has been credited with INR {amount} on {date}as {description}.",
            "Pluxee Wallet Load" to 5L to "credited with Rs.{amount} towards{wallet} on {description}. Your",
            "DCB POS/Ecom Debit" to 6L to "INR {amount} debited DCB Bank a/c*{card} POS/Ecom txn to {description} on {date}"
        )
        rules.forEachIndexed { index, (descBankId, pattern) ->
            val (desc, bankId) = descBankId
            db.execSQL(
                "INSERT INTO sms_rules (id, bankId, pattern, description) VALUES(${index + 1}, $bankId, '${pattern.replace("'", "''")}', '${desc.replace("'", "''")}')"
            )
        }

    }

}