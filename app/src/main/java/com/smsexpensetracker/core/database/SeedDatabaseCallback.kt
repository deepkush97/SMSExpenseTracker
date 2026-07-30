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
            "Pluxee" to "PLXEE"
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
            val isDefault = if (name == "Other") 1 else 0

            db.execSQL(
                "INSERT INTO categories (id, name, icon, color, isDefault) VALUES (${index + 1}, '$name','$icon', $color, $isDefault)"
            )
        }
    }

    private fun seedSmsRules(db: SupportSQLiteDatabase) {

        val rules = listOf(
            "HDFC CC Debit" to 1L to "Spent Rs\\.([\\d,.]+) On HDFC Bank Card \\d{4} At (.+?) On .+",
            "HDFC UPI Credit" to 1L to "Rs\\.([\\d,.]+) credited to HDFC Bank A/c \\w+ on [\\d-]+ from VPA (.+?) \\(UPI",
            "HDFC e-Mandate" to 1L to "INR ([\\d,.]+) deducted from HDFC Bank A/C No \\w+ towards (.+?) UMRN",
            "HDFC NEFT Credit" to 1L to "INR ([\\d,.]+) deposited in HDFC Bank A/c \\w+ on [\\w-]+ for NEFT Cr-(.+?)\\.?Avl bal",
            "ICICI UPI Debit" to 2L to "ICICI Bank Acct \\w+ debited for Rs ([\\d,.]+) on [\\d-]+; (.+?) credited\\. UPI",
            "ICICI UPI Credit" to 2L to "Acct \\w+ is credited with Rs ([\\d,.]+) on [\\d-]+ from (.+?)\\. UPI"
        )
        rules.forEachIndexed { index, (descBankId, pattern) ->
            val (desc, bankId) = descBankId
            db.execSQL(
                "INSERT INTO sms_rules (id, bankId, pattern, description) VALUES(${index + 1}, $bankId, '${pattern.replace("'", "''")}', '${desc.replace("'", "''")}')"
            )
        }

    }

}