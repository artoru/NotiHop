package com.artor.notihop.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.UUID

class BluetoothServer(
    private val adapter: BluetoothAdapter,
    private val log: (String) -> Unit = {}
) {
    private var serverSocket: BluetoothServerSocket? = null

    @SuppressLint("MissingPermission")
    fun accept(): BluetoothSocket? {
        // Always (re)create the server socket so the SDP record is fresh.
        // The window where no SDP record is registered is now microseconds,
        // not the full duration of a connection — which was the old problem.
        try { serverSocket?.close() } catch (_: IOException) {}

        log("listenUsingRfcommWithServiceRecord()")
        val ss = try {
            adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
        } catch (e: Exception) {
            log("listen FAILED: ${e.javaClass.simpleName}: ${e.message}")
            return null
        }
        serverSocket = ss

        log("serverSocket.accept()… (blocking)")
        return try {
            val client = ss.accept()
            log("accept() returned client: ${client.remoteDevice.address}")
            client
        } catch (e: IOException) {
            log("accept() FAILED: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    fun cancel() {
        try { serverSocket?.close() } catch (_: IOException) {}
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        const val SERVICE_NAME = "NotiHopNotification"
    }
}
