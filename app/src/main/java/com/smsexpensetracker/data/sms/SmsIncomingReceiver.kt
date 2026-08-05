package com.smsexpensetracker.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.smsexpensetracker.domain.usecase.SmsSyncUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsIncomingReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsSyncUseCase: SmsSyncUseCase

    @Inject
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        if (body.isBlank()) return

        val pendingResult = goAsync()
        appScope.launch {
            try {
                smsSyncUseCase.handleIncomingSms(body, sender, timestamp)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
