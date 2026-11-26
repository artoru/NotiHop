package com.artor.notihop.bluetooth

import com.artor.notihop.model.SharedNotification
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object MessageProtocol {
    private val json = Json { ignoreUnknownKeys = true }

    fun write(out: OutputStream, notification: SharedNotification) {
        val bytes = json.encodeToString(notification).toByteArray(Charsets.UTF_8)
        val len = bytes.size
        out.write(byteArrayOf(
            (len ushr 24).toByte(),
            (len ushr 16).toByte(),
            (len ushr 8).toByte(),
            len.toByte()
        ))
        out.write(bytes)
        out.flush()
    }

    fun read(input: InputStream): SharedNotification? {
        val lenBytes = ByteArray(4)
        if (!readFully(input, lenBytes)) return null
        val len = ((lenBytes[0].toInt() and 0xFF) shl 24) or
                  ((lenBytes[1].toInt() and 0xFF) shl 16) or
                  ((lenBytes[2].toInt() and 0xFF) shl 8) or
                   (lenBytes[3].toInt() and 0xFF)
        if (len < 0 || len > 512_000) return null
        val data = ByteArray(len)
        if (!readFully(input, data)) return null
        return json.decodeFromString(String(data, Charsets.UTF_8))
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n == -1) return false
            offset += n
        }
        return true
    }
}
