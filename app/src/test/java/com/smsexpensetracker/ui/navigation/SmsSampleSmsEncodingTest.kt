package com.smsexpensetracker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsSampleSmsEncodingTest {

    private val hexDigits = "0123456789ABCDEF".toCharArray()

    @Test
    fun `encode matches AOSP output for reserved chars`() {
        assertEquals(
            "50%25%20off%20at%20Store%20%26%20Cafe%20%2F%20corner%2C%20100%25%2B%20great%20%231",
            androidEncode("50% off at Store & Cafe / corner, 100%+ great #1")
        )
        assertEquals(
            "Spent%20Rs.1250.50%20On%20HDFC%20Bank%20Card%201234%20At%20Coffee%20Shop%20On%2001-Aug",
            androidEncode("Spent Rs.1250.50 On HDFC Bank Card 1234 At Coffee Shop On 01-Aug")
        )
    }

    @Test
    fun `encode then decode is lossless for SMS bodies`() {
        val bodies = listOf(
            "Spent Rs.1250.50 On HDFC Bank Card 1234 At Coffee Shop On 01-Aug",
            "Acct 1234 debited Rs 500.00 for Swiggy; payee+ref=ABC&DEF #3",
            "50% off at Store & Cafe / corner, 100%+ great #1"
        )
        bodies.forEach { body ->
            assertEquals(body, androidDecode(androidEncode(body)))
        }
    }

    // The port mirrors Uri.decode (convertPlus=false): a literal '+' is NOT decoded
    // to a space. That is safe here because Uri.encode emits %2B for '+', so no raw
    // '+' reaches the navigation parser, which decodes with convertPlus=true via
    // Uri.getQueryParameter.
    private fun androidEncode(s: String): String {
        if (s.isEmpty()) return s
        val encoded = StringBuilder()
        var current = 0
        while (current < s.length) {
            var nextToEncode = current
            while (nextToEncode < s.length && isAllowed(s[nextToEncode])) nextToEncode++
            if (nextToEncode == s.length) {
                encoded.append(s, current, s.length)
                return encoded.toString()
            }
            if (nextToEncode > current) encoded.append(s, current, nextToEncode)
            var nextAllowed = nextToEncode + 1
            while (nextAllowed < s.length && !isAllowed(s[nextAllowed])) nextAllowed++
            val bytes = s.substring(nextToEncode, nextAllowed).toByteArray(Charsets.UTF_8)
            for (b in bytes) {
                encoded.append('%')
                encoded.append(hexDigits[(b.toInt() and 0xf0) shr 4])
                encoded.append(hexDigits[b.toInt() and 0xf])
            }
            current = nextAllowed
        }
        return encoded.toString()
    }

    private fun androidDecode(s: String): String {
        if (!s.contains('%')) return s
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val d1 = Character.digit(s[i + 1], 16)
                val d2 = Character.digit(s[i + 2], 16)
                if (d1 != -1 && d2 != -1) {
                    bytes.write((d1 shl 4) or d2)
                    i += 3
                    continue
                }
            }
            bytes.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun isAllowed(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || "_-!.~'()*".indexOf(c) != -1
}
