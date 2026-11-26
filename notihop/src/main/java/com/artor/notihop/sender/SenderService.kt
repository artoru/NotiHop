package com.artor.notihop.sender

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.artor.notihop.LogBuffer
import com.artor.notihop.MainActivity
import com.artor.notihop.Prefs
import com.artor.notihop.R
import com.artor.notihop.bluetooth.BluetoothClient
import com.artor.notihop.bluetooth.MessageProtocol
import com.artor.notihop.model.SharedNotification
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.IOException

class SenderService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = Channel<SharedNotification>(Channel.UNLIMITED)
    private var targetDevice: BluetoothDevice? = null
    private var btAdapter: BluetoothAdapter? = null
    private val phoneCallReceiver = PhoneCallReceiver()

    companion object {
        var instance: SenderService? = null
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        private const val CHANNEL_ID = "notihop_sender_v1"
        private const val NOTIF_ID = 1
        private const val TAG = "SenderService"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        debugLog("onCreate start")
        createChannel()
        debugLog("channel created")
        try {
            startForeground(NOTIF_ID, buildForegroundNotif())
            debugLog("startForeground OK")
        } catch (e: Exception) {
            debugLog("startForeground THREW: ${e.javaClass.simpleName}: ${e.message}")
        }
        if (!Prefs.isPersistentNotifications(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        @Suppress("DEPRECATION")
        registerReceiver(phoneCallReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
        debugLog("onStartCommand address=$address")
        if (address == null) return START_NOT_STICKY
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        btAdapter = adapter
        targetDevice = adapter.getRemoteDevice(address)
        debugLog("BT adapter state=${adapter.state} bondedDevices=${adapter.bondedDevices.size}")
        scope.launch { notificationWorker() }
        return START_STICKY
    }

    private suspend fun notificationWorker() {
        val device = targetDevice ?: return
        val adapter = btAdapter ?: return

        broadcast("Ready — waiting for notifications")
        while (currentCoroutineContext().isActive) {
            // Block here until a notification arrives — no connection held while idle
            val first = queue.receive()

            // Connect, retrying on failure while holding the notification
            var socket: BluetoothSocket? = null
            var attempt = 0
            val maxAttempts = 5
            while (currentCoroutineContext().isActive && socket == null && attempt < maxAttempts) {
                attempt++
                broadcast("Connecting… (attempt $attempt/$maxAttempts)")
                socket = BluetoothClient(adapter, device) { debugLog(it) }.connect()
                if (socket == null && attempt < maxAttempts) delay(3_000)
            }
            if (socket == null) {
                broadcast("Cannot connect — receiver not reachable after $maxAttempts attempts")
                debugLog("gave up connecting after $maxAttempts attempts")
                // Drain stale queued notifications
                while (true) { queue.tryReceive().getOrNull() ?: break }
                continue
            }
            val s = socket

            broadcast("Sending…")
            try {
                val out = s.outputStream
                MessageProtocol.write(out, first)
                debugLog("sent: ${first.appLabel} / ${first.title}")

                // Drain any notifications that piled up while connecting,
                // staying connected for up to 5 s after the last one
                while (currentCoroutineContext().isActive) {
                    val next = withTimeoutOrNull(5_000) { queue.receive() } ?: break
                    MessageProtocol.write(out, next)
                    debugLog("sent: ${next.appLabel} / ${next.title}")
                }
            } catch (e: IOException) {
                debugLog("send failed: ${e.message}")
            } finally {
                try { s.close() } catch (_: IOException) {}
            }

            broadcast("Ready — waiting for notifications")
            debugLog("disconnected — idle")
        }
    }

    fun showStatusNotification() = startForeground(NOTIF_ID, buildForegroundNotif())
    fun hideStatusNotification() = stopForeground(STOP_FOREGROUND_REMOVE)

    fun sendNotification(n: SharedNotification) {
        queue.trySend(n)
    }

    private fun broadcast(msg: String) = LogBuffer.status(msg)

    private fun debugLog(msg: String) = LogBuffer.append("Sender", msg)

    override fun onDestroy() {
        debugLog("onDestroy")
        instance = null
        scope.cancel()
        queue.close()
        unregisterReceiver(phoneCallReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "NotiHop Sender", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildForegroundNotif(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotiHop — Sender active")
            .setContentText("Forwarding notifications via Bluetooth…")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
    }
}
