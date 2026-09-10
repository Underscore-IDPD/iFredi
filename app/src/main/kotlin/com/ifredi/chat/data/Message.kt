package com.ifredi.chat.data
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
    val isRead: Boolean = false,
    val readAt: Long? = null,
    val status: MessageStatus = MessageStatus.PENDING,
    val type: MessageType = MessageType.TEXT
) {

    // Verifica si el mensaje fue enviado por el usuario actual
    fun isOwn(userId: String): Boolean = senderId == userId

    // Retorna true si el mensaje contiene una imagen
    fun isImage(): Boolean = type == MessageType.IMAGE && !imageUrl.isNullOrBlank()

    // Retorna true si el mensaje es un mensaje del sistema
    fun isSystem(): Boolean = type == MessageType.SYSTEM

    // Retorna la hora de envío formateada como HH:mm
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