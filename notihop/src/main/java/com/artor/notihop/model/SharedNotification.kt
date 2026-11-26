package com.artor.notihop.model

import kotlinx.serialization.Serializable

@Serializable
data class MessagingMessage(
    val senderName: String? = null,
    val text: String = "",
    val timestamp: Long = 0L
)

@Serializable
data class SharedNotification(
    val type: String = "notification",
    val appPackage: String = "",
    val appLabel: String = "",
    val title: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val iconBase64: String? = null,
    val messagingMessages: List<MessagingMessage>? = null,
    val conversationTitle: String? = null,
    val appIconBase64: String? = null
)
