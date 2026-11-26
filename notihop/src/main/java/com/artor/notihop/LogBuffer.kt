package com.artor.notihop

import android.os.Handler
import android.os.Looper
import android.util.Log

object LogBuffer {
    private val handler = Handler(Looper.getMainLooper())
    private val entries = mutableListOf<String>()
    private var lastStatus: String? = null

    var onNewEntry: ((String) -> Unit)? = null

    var onStatusUpdate: ((String) -> Unit)? = null
        set(value) {
            field = value
            // Replay the last known status so the UI is correct even if the
            // message was sent before onResume() registered the callback.
            lastStatus?.let { s -> handler.post { value?.invoke(s) } }
        }

    fun append(tag: String, msg: String) {
        val line = "[$tag] $msg"
        Log.d("NotiHop", line)
        synchronized(entries) { entries.add(line) }
        handler.post { onNewEntry?.invoke(line) }
    }

    fun status(msg: String) {
        lastStatus = msg
        Log.d("NotiHop/status", msg)
        handler.post { onStatusUpdate?.invoke(msg) }
    }

    fun getAll(): List<String> = synchronized(entries) { entries.toList() }
}
