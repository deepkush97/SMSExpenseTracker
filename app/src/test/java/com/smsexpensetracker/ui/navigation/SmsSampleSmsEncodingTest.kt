package com.smsexpensetracker.ui.navigation

import android.net.Uri
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SmsSampleSmsEncodingTest {

    private val hexDigits = "0123456789ABCDEF".toCharArray()

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.encode(any()) } answers { androidEncode(firstArg()) }
        every { Uri.decode(any()) } answers { androidDecode(firstArg()) }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `encode then decode is lossless for SMS bodies with reserved characters`() {
        val bodies = listOf(
            "Spent Rs.1250.50 On HDFC Bank Card 1234 At Coffee Shop On 01-Aug",
            "Acct 1234 debited Rs 500.00 for Swiggy; payee+ref=ABC&DEF #3",
            "50% off at Store & Cafe / corner, 100%+ great #1"
        )
        bodies.forEach { body ->
            val roundTripped = Uri.decode(Uri.encode(body))
            assertEquals(body, roundTripped)
        }
    }

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
