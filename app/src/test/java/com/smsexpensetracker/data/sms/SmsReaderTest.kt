package com.smsexpensetracker.data.sms

import android.content.ContentResolver
import android.database.Cursor
import android.provider.Telephony
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsReaderTest {
    private val contentResolver = mockk<ContentResolver>()
    private val cursor = mockk<Cursor>(relaxed = true)
    private val reader = SmsReader(contentResolver)

    @Test
    fun `read multiple SMS from inbox`() = runTest {
        var cursorPosition = 0
        every { cursor.moveToNext() } answers { ++cursorPosition <= 2 }
        every { cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox._ID) } returns 0
        every { cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS) } returns 1
        every { cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY) } returns 2
        every { cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE) } returns 3
        every { cursor.getLong(0) } returnsMany listOf(1L, 2L)
        every { cursor.getString(1) } returnsMany listOf("AD-HDFCBK-S", "AD-ICICIT-S")
        every { cursor.getString(2) } returnsMany listOf(
            "Spent Rs.100.00",
            "Acct credited Rs.50.00"
        )
        every { cursor.getLong(3) } returnsMany listOf(1000L, 2000L)
        every {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                null,
                null,
                null,
                "${Telephony.Sms.Inbox.DATE} DESC"
            )
        } returns cursor

        val result = reader.readSms().first()

        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("AD-HDFCBK-S", result[0].sender)
        assertEquals("Spent Rs.100.00", result[0].body)
        assertEquals(1000L, result[0].timestamp)
        assertEquals(2L, result[1].id)
        assertEquals("AD-ICICIT-S", result[1].sender)
        assertEquals("Acct credited Rs.50.00", result[1].body)
        assertEquals(2000L, result[1].timestamp)
        verify { cursor.close() }
    }

    @Test
    fun `returns empty list when the inbox is empty`() = runTest {
        every { cursor.moveToNext() } returns false
        every {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                null,
                null,
                null,
                "${Telephony.Sms.Inbox.DATE} DESC"
            )
        } returns cursor

        val result = reader.readSms().first()

        assertEquals(0, result.size)
        verify { cursor.close() }
    }

    @Test
    fun `returns empty list when the query returns null`() = runTest {

        every {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                null,
                null,
                null,
                "${Telephony.Sms.Inbox.DATE} DESC"
            )
        } returns null

        val result = reader.readSms().first()
        assertEquals(0, result.size)
    }

    @Test
    fun `filter by sender`() = runTest {
        every { cursor.moveToNext() } returns false
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor

        reader.readSms(senderFilter = "HDFCBK").first()

        verify {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                null,
                "address LIKE ?",
                arrayOf("%HDFCBK%"),
                "${Telephony.Sms.Inbox.DATE} DESC"
            )
        }
        verify { cursor.close() }
    }

    @Test
    fun `filter by date range`() = runTest {
        every { cursor.moveToNext() } returns false
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor

        reader.readSms(dateRange = 1000L to 2000L).first()

        verify {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                null,
                "date BETWEEN ? AND ?",
                arrayOf("1000", "2000"),
                "${Telephony.Sms.Inbox.DATE} DESC"
            )
        }
        verify { cursor.close() }
    }

    @Test
    fun `filter by sender and date range combined`() = runTest {
        every { cursor.moveToNext() } returns false
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor

        reader.readSms(senderFilter = "HDFCBK", dateRange = 1000L to 2000L).first()

        verify {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                null,
                "address LIKE ? AND date BETWEEN ? AND ?",
                arrayOf("%HDFCBK%", "1000", "2000"),
                "${Telephony.Sms.Inbox.DATE} DESC"
            )
        }
        verify { cursor.close() }
    }
}