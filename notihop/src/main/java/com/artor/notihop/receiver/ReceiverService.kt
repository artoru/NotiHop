package com.artor.notihop.receiver

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.artor.notihop.LogBuffer
import com.artor.notihop.MainActivity
import com.artor.notihop.Prefs
import com.artor.notihop.R
import com.artor.notihop.bluetooth.BluetoothServer
import com.artor.notihop.bluetooth.MessageProtocol
import kotlinx.coroutines.*
import java.io.IOException

class ReceiverService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var server: BluetoothServer? = null
    private var socket: BluetoothSocket? = null

    companion object {
        var instance: ReceiverService? = null
        private const val CHANNEL_ID = "notihop_receiver_v1"
        private const val NOTIF_ID = 2
        private const val TAG = "ReceiverService"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        debugLog("onCreate start")
        createChannel()
        debugLog("channel created")
        NotificationPoster.ensureChannel(this)
        debugLog("calling startForeground")
        try {
            startForeground(NOTIF_ID, buildForegroundNotif())
            debugLog("startForeground OK")
        } catch (e: Exception) {
            debugLog("startForeground THREW: ${e.javaClass.simpleName}: ${e.message}")
        }
        if (!Prefs.isPersistentNotifications(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        debugLog("onStartCommand — BT adapter state=${adapter.state}")
        server = BluetoothServer(adapter) { debugLog(it) }
        scope.launch { acceptLoop() }
        return START_STICKY
    }

    private suspend fun acceptLoop() {
        var srv = server ?: return
        var consecutiveFailures = 0
        while (currentCoroutineContext().isActive) {
            broadcast("Waiting for notifications via Bluetooth…")
            debugLog("calling accept()…")
            socket = srv.accept()
            if (socket != null) {
                consecutiveFailures = 0
                val msg = "Sender connected: ${socket!!.remoteDevice.address}"
                broadcast(msg); debugLog(msg)
                readLoop()
                broadcast("Sender disconnected"); debugLog("readLoop ended")
            } else {
                consecutiveFailures++
                if (consecutiveFailures % 5 == 0) {
                    debugLog("Recreating BluetoothServer after $consecutiveFailures consecutive failures")
                    srv.cancel()
                    val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    srv = BluetoothServer(adapter) { debugLog(it) }
                    server = srv
                }
                delay(2_000)
            }
        }
    }

    private suspend fun readLoop() {
        val s = socket ?: return
        try {
            while (currentCoroutineContext().isActive) {
                val n = MessageProtocol.read(s.inputStream) ?: break
                debugLog("received: ${n.appLabel} / ${n.title}")
                NotificationPoster.post(this@ReceiverService, n)
            }
        } catch (e: IOException) {
            debugLog("readLoop IOException: ${e.message}")
        } finally {
            try { s.close() } catch (_: IOException) {}
            socket = null
        }
    }

    fun showStatusNotification() = startForeground(NOTIF_ID, buildForegroundNotif())
    fun hideStatusNotification() = stopForeground(STOP_FOREGROUND_REMOVE)

    private fun broadcast(msg: String) = LogBuffer.status(msg)

    private fun debugLog(msg: String) = LogBuffer.append("Receiver", msg)

    override fun onDestroy() {
        instance = null
        debugLog("onDestroy")
        scope.cancel()
        server?.cancel()
        try { socket?.close() } catch (_: IOException) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "NotiHop Receiver", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildForegroundNotif(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotiHop — Receiver active")
            .setContentText("Waiting for notifications via Bluetooth…")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
    }
}
