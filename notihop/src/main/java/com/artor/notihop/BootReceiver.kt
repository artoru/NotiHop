package com.artor.notihop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.artor.notihop.receiver.ReceiverService
import com.artor.notihop.sender.SenderService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.isAutostartEnabled(context)) return
        autoStart(context)
    }

    companion object {
        fun autoStart(context: Context) {
            when (Prefs.getMode(context)) {
                Prefs.MODE_RECEIVER -> {
                    context.startForegroundService(Intent(context, ReceiverService::class.java))
                }
                Prefs.MODE_SENDER -> {
                    val address = Prefs.getDeviceAddress(context) ?: return
                    context.startForegroundService(
                        Intent(context, SenderService::class.java)
                            .putExtra(SenderService.EXTRA_DEVICE_ADDRESS, address)
                    )
                }
            }
        }
    }
}
