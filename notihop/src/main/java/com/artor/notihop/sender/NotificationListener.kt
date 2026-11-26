package com.artor.notihop.sender

import android.app.Notification
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import com.artor.notihop.LogBuffer
import com.artor.notihop.Prefs
import com.artor.notihop.model.MessagingMessage
import com.artor.notihop.model.SharedNotification
import java.io.ByteArrayOutputStream

@Suppress("DEPRECATION")
private fun Bundle.getParcelableArrayCompat(key: String): Array<out Parcelable>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        getParcelableArray(key, Bundle::class.java)
    else
        getParcelableArray(key)

@Suppress("DEPRECATION")
private fun Bundle.getParcelableUriCompat(key: String): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        getParcelable(key, Uri::class.java)
    else
        getParcelable(key)

@Suppress("DEPRECATION")
private fun Bundle.getBitmapCompat(key: String): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        getParcelable(key, Bitmap::class.java)
    else
        getParcelable(key)

class NotificationListener : NotificationListenerService() {

    companion object {
        private val iconCache = HashMap<String, String>()
        // Tracks how many EXTRA_MESSAGES have been forwarded per notification key,
        // so updates to the same conversation only forward when there are new messages.
        private val sentMessageCounts = HashMap<String, Int>()
    }

    private fun encodeBitmap(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun encodeIconFromUri(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
            }
        } catch (_: Exception) { null }
    }

    private fun encodeIconFromMessages(notification: Notification): String? {
        val messages = notification.extras.getParcelableArrayCompat(Notification.EXTRA_MESSAGES)
            ?: return null
        if (messages.isEmpty()) return null
        val uri = (messages.last() as? Bundle)?.getParcelableUriCompat("uri") ?: return null
        return encodeIconFromUri(uri)
    }

    private fun encodeIconFromExtras(notification: Notification): String? {
        val extras = notification.extras
        extras.getBitmapCompat(Notification.EXTRA_PICTURE)?.let { return encodeBitmap(it) }
        // getLargeIcon() is API 23+; minSdk is 26 so no version check needed
        val icon = notification.getLargeIcon() ?: return null
        val drawable = icon.loadDrawable(this) ?: return null
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, 96, 96)
        drawable.draw(canvas)
        return encodeBitmap(bitmap)
    }

    // Cross-profile lookup (e.g. a work-profile app whose notifications are relayed into the
    // personal profile): packageManager only sees packages in this process's own user, so it
    // throws for a work-profile package. LauncherApps is the API designed to resolve labels/
    // icons across profiles associated with the current user (personal <-> work), and needs no
    // extra permission beyond the notification listener access this service already requires.
    private fun launcherActivityInfo(pkg: String, user: UserHandle) = try {
        val list = getSystemService(LauncherApps::class.java)?.getActivityList(pkg, user)
        LogBuffer.append("Icon", "getActivityList pkg=$pkg user=$user -> ${list?.size ?: "null"} result(s)")
        list?.firstOrNull()
    } catch (e: Exception) {
        LogBuffer.append("Icon", "getActivityList pkg=$pkg user=$user threw ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    private fun resolveAppLabel(pkg: String, user: UserHandle): String {
        launcherActivityInfo(pkg, user)?.let {
            LogBuffer.append("Icon", "label for $pkg resolved via LauncherApps: ${it.label}")
            return it.label.toString()
        }
        return try {
            val label = packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
            LogBuffer.append("Icon", "label for $pkg resolved via local packageManager: $label")
            label
        } catch (e: PackageManager.NameNotFoundException) {
            LogBuffer.append("Icon", "label for $pkg: both LauncherApps and packageManager failed (${e.message}), using raw pkg name")
            pkg
        }
    }

    private fun encodeIcon(pkg: String, user: UserHandle): String? {
        val cacheKey = "${user.hashCode()}:$pkg"
        iconCache[cacheKey]?.let { return it }
        if (iconCache.size >= 200) iconCache.clear()
        val drawable = launcherActivityInfo(pkg, user)?.getBadgedIcon(resources.configuration.densityDpi)
            ?: try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
            ?: return null
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, 96, 96)
        drawable.draw(canvas)
        val b64 = encodeBitmap(bitmap)
        iconCache[cacheKey] = b64
        return b64
    }

    // Returns null if no EXTRA_MESSAGES or no new messages since last forward.
    // Otherwise returns ALL currently visible messages for full conversation sync.
    private fun extractMessagingMessages(
        notificationKey: String,
        extras: Bundle
    ): List<MessagingMessage>? {
        val messages = extras.getParcelableArrayCompat(Notification.EXTRA_MESSAGES) ?: return null
        if (messages.isEmpty()) return null
        val alreadySent = sentMessageCounts[notificationKey] ?: 0
        val newCount = messages.size
        if (newCount <= alreadySent) return null
        sentMessageCounts[notificationKey] = newCount
        return messages.mapNotNull { (it as? Bundle)?.let { b ->
            MessagingMessage(
                senderName = b.getCharSequence("sender")?.toString(),
                text = b.getCharSequence("text")?.toString() ?: return@mapNotNull null,
                timestamp = b.getLong("time", 0L)
            )
        }}
    }

    // Returns null if there is nothing new to forward.
    private fun extractNewText(extras: Bundle): String? {
        // BigTextStyle: full expanded text
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }?.let { return it }
        // InboxStyle: join all lines
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!lines.isNullOrEmpty()) {
            return lines.joinToString("\n") { it.toString() }
        }
        return extras.getCharSequence("android.text")?.toString() ?: ""
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return
        val flags = sbn.notification.flags
        if (flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: return
        LogBuffer.append("Icon", "onNotificationPosted pkg=${sbn.packageName} sbn.user=${sbn.user} (myUser=${android.os.Process.myUserHandle()})")

        val messagingMessages = extractMessagingMessages(sbn.key, extras)
        val text: String
        val conversationTitle: String?
        if (messagingMessages != null) {
            text = messagingMessages.joinToString("\n") { it.text }
            conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?.toString() ?: title
        } else {
            text = extractNewText(extras) ?: return
            conversationTitle = null
        }

        val appLabel = resolveAppLabel(sbn.packageName, sbn.user)

        val pkg = sbn.packageName
        if (!Prefs.isKnownFilterApp(this, pkg)) {
            val autoEnable = Prefs.isAutoEnableNewApps(this)
            Prefs.addFilterApp(this, pkg, appLabel, enabled = autoEnable)
            if (!autoEnable) return
        } else if (!Prefs.isFilterAppEnabled(this, pkg)) {
            return
        }

        // For messaging notifications: iconBase64 = contact photo, appIconBase64 = source app icon.
        // For others: iconBase64 = best available icon, appIconBase64 unused.
        val iconBase64: String?
        val appIconBase64: String?
        if (messagingMessages != null) {
            iconBase64 = encodeIconFromMessages(sbn.notification)
                ?: encodeIconFromExtras(sbn.notification)
            appIconBase64 = encodeIcon(pkg, sbn.user)
        } else {
            iconBase64 = encodeIconFromMessages(sbn.notification)
                ?: encodeIconFromExtras(sbn.notification)
                ?: encodeIcon(pkg, sbn.user)
            appIconBase64 = null
        }

        val notification = SharedNotification(
            appPackage = pkg,
            appLabel = appLabel,
            title = title,
            text = text,
            timestamp = sbn.postTime,
            iconBase64 = iconBase64,
            messagingMessages = messagingMessages,
            conversationTitle = conversationTitle,
            appIconBase64 = appIconBase64
        )
        SenderService.instance?.sendNotification(notification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        sentMessageCounts.remove(sbn.key)
    }
}
