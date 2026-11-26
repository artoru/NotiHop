package com.artor.notihop.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.artor.notihop.LogBuffer
import com.artor.notihop.R
import com.artor.notihop.model.SharedNotification

object NotificationPoster {
    private const val CHANNEL_ID = "notihop_received_v1"

    private val conversationIds = HashMap<String, Int>()

    private fun overlayAppBadge(base: Bitmap, badge: Bitmap): Bitmap {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val badgeSize = result.width / 3
        val radius = badgeSize * 0.28f
        val left = (result.width - badgeSize).toFloat()
        val top = (result.height - badgeSize).toFloat()
        val scaled = Bitmap.createScaledBitmap(badge, badgeSize, badgeSize, true)
        val path = Path().apply {
            addRoundRect(RectF(left, top, result.width.toFloat(), result.height.toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(scaled, left, top, null)
        canvas.restore()
        return result
    }

    private fun conversationKey(n: SharedNotification) =
        "${n.appPackage}:${n.conversationTitle ?: n.title}"

    private fun stableId(key: String): Int =
        conversationIds.getOrPut(key) {
            val h = Math.abs(key.hashCode())
            if (h == 0) 1 else h
        }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Received Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }
        )
        val ch = nm.getNotificationChannel(CHANNEL_ID)
        LogBuffer.append("Poster", "channel importance=${ch?.importance} blocked=${ch?.importance == NotificationManager.IMPORTANCE_NONE}")
    }

    fun post(context: Context, n: SharedNotification) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ch = nm.getNotificationChannel(CHANNEL_ID)
        val enabled = nm.areNotificationsEnabled()
        LogBuffer.append("Poster", "post() '${n.title}' enabled=$enabled channelImportance=${ch?.importance}")
        val largeIcon = n.iconBase64?.let { b64 ->
            runCatching {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
        val msgs = n.messagingMessages
        if (msgs != null) {
            val key = conversationKey(n)
            val me = Person.Builder().setName("Me").build()
            val conversationDisplayTitle = n.conversationTitle?.takeIf { it != n.title }
            val appIcon = n.appIconBase64?.let { b64 ->
                runCatching {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            val style = NotificationCompat.MessagingStyle(me)
                .also { if (conversationDisplayTitle != null) it.setConversationTitle(conversationDisplayTitle) }
            // Contact photo with rounded app badge overlaid, used as per-message Person icon
            val contactBitmap = if (largeIcon != null && appIcon != null)
                overlayAppBadge(largeIcon, appIcon) else largeIcon
            val contactIcon = contactBitmap?.let { IconCompat.createWithBitmap(it) }
            for (msg in msgs) {
                val sender = msg.senderName?.let {
                    Person.Builder().setName(it)
                        .also { b -> if (contactIcon != null) b.setIcon(contactIcon) }
                        .build()
                }
                style.addMessage(NotificationCompat.MessagingStyle.Message(msg.text, msg.timestamp, sender))
            }
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(n.title)
                .setContentText(n.text)
                .setSmallIcon(R.drawable.ic_notification)
                .setStyle(style)
                .apply { if (appIcon != null) setLargeIcon(appIcon) }
                .setWhen(n.timestamp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val id = stableId(key)
            nm.notify(id, notif)
            LogBuffer.append("Poster", "nm.notify() messaging id=$id key=$key msgs=${msgs.size}")
        } else {
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(n.title)
                .setContentText(n.text)
                .setSmallIcon(R.drawable.ic_notification)
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
                .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
                .setWhen(n.timestamp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val id = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
            nm.notify(id, notif)
            LogBuffer.append("Poster", "nm.notify() id=$id done")
        }
    }
}
