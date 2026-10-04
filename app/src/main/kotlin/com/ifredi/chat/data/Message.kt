package com.ifredi.chat.data

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderImageUrl: String? = null,
    val text: String = "",
    val imageUrl: String? = null,
    val timestamp: Long = 0L,

    // Force Firestore to map this exactly to "isRead"
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false,

    val readAt: Long? = null,
    val status: MessageStatus = MessageStatus.PENDING,
    val type: MessageType = MessageType.TEXT
) {

    // Exclude helper functions so they aren't saved to the database
    @Exclude
    fun isOwn(userId: String): Boolean = senderId == userId

    @Exclude
    fun isImage(): Boolean = type == MessageType.IMAGE && !imageUrl.isNullOrBlank()

    @Exclude
    fun isSystem(): Boolean = type == MessageType.SYSTEM

    @Exclude
    fun getFormattedTime(): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}

enum class MessageStatus {

    PENDING,

    SENT,

    DELIVERED,

    READ,

    FAILED

}



enum class MessageType {

    TEXT,

    IMAGE,

    SYSTEM

}

