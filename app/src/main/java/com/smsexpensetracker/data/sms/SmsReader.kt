package com.smsexpensetracker.data.sms

import android.content.ContentResolver
import android.provider.Telephony
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SmsReader(
    private val contentResolver: ContentResolver
) {
    fun readSms(
        senderFilter: String? = null,
        dateRange: Pair<Long, Long>? = null
    ): Flow<List<SmsMessage>> = flow {
        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            null,
            buildSelection(senderFilter, dateRange),
            buildSelectionArgs(senderFilter, dateRange),
            "${Telephony.Sms.Inbox.DATE} DESC"
        )

        val messages = mutableListOf<SmsMessage>()
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox._ID))
                val sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)) ?: ""
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)) ?: ""
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE))
                messages.add(SmsMessage(id, sender, body, timestamp))
            }
            cursor.close()
        }
        emit(messages)
    }

    private fun buildSelection(
        senderFilter: String?,
        dateRange: Pair<Long, Long>?
    ): String? {
        val conditions = mutableListOf<String>()
        if (senderFilter != null) conditions.add("${Telephony.Sms.ADDRESS} LIKE ?")
        if (dateRange != null) conditions.add("${Telephony.Sms.Inbox.DATE} BETWEEN ? AND ?")
        return conditions.joinToString(" AND ").ifEmpty { null }
    }

    private fun buildSelectionArgs(
        senderFilter: String?,
        dateRange: Pair<Long, Long>?
    ): Array<String>? {
        val args = mutableListOf<String>()
        if (senderFilter != null) args.add("%$senderFilter%")
        if (dateRange != null) {
            args.add(dateRange.first.toString())
            args.add(dateRange.second.toString())
        }
        return args.toTypedArray().ifEmpty { null }
    }
}