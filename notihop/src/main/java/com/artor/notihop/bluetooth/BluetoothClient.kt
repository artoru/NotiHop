package com.artor.notihop.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException

class BluetoothClient(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val log: (String) -> Unit = {}
) {
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    fun connect(): BluetoothSocket? {
        log("cancelDiscovery()")
        adapter.cancelDiscovery()

        log("createRfcommSocketToServiceRecord(${device.address})")
        val s = try {
            device.createRfcommSocketToServiceRecord(BluetoothServer.SERVICE_UUID)
        } catch (e: Exception) {
            log("createRfcomm FAILED: ${e.javaClass.simpleName}: ${e.message}")
            return null
        }
        socket = s

        log("socket.connect()…")
        return try {
            s.connect()
            log("socket.connect() OK")
            s
        } catch (e: IOException) {
            log("socket.connect() FAILED: ${e.javaClass.simpleName}: ${e.message}")
            try { s.close() } catch (_: IOException) {}
            socket = null
            null
        }
    }

    fun cancel() {
        try { socket?.close() } catch (_: IOException) {}
    }
}
