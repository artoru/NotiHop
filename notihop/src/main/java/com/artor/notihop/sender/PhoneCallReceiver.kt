package com.artor.notihop.sender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import com.artor.notihop.LogBuffer
import com.artor.notihop.Prefs
import com.artor.notihop.model.SharedNotification

class PhoneCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        if (state != TelephonyManager.EXTRA_STATE_RINGING) return
        if (!Prefs.isPhoneCallForwardEnabled(context)) return

        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val callerText = when {
            number == null -> "Incoming phone call"
            else -> lookupContactName(context, number) ?: number
        }

        LogBuffer.append("PhoneCall", "incoming call: $callerText — forwarding")
        SenderService.instance?.sendNotification(
            SharedNotification(
                appPackage = "android.telecom",
                appLabel = "Phone",
                title = "Incoming Call",
                text = callerText,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun lookupContactName(context: Context, number: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
}
