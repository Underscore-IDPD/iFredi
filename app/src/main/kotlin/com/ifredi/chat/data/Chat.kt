package com.ifredi.chat.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: List<String> = emptyList(),
    val type: ChatType = ChatType.PRIVATE,
    val name: String? = null,
    val groupImageUrl: String? = null,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastMessageSenderId: String? = null,
    val unreadCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false
) {

    // Retorna el nombre para mostrar del chat, dependiendo del tipo de chat y del usuario actual
    fun getDisplayName(currentUserId: String): String {
        if (type == ChatType.GROUP) {
            return name ?: "Grupo"
        }
        val otherIndex = participants.indexOfFirst { it != currentUserId }
        return participantNames.getOrNull(otherIndex) ?: "Usuario"
    }

    // Retorna el ID del otro participante en un chat privado, o null si es un chat grupal
    fun getOtherUserId(currentUserId: String): String? {
        if (type == ChatType.GROUP) return null
        return participants.firstOrNull { it != currentUserId }
    }

    // Verifica si el chat tiene mensajes sin leer
    fun hasUnread(): Boolean = unreadCount > 0

    // Formatea la hora del último mensaje como HH:mm
    fun getFormattedLastMessageTime(): String {
        if (lastMessageTime == 0L) return ""
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(lastMessageTime))
    }
}

enum class ChatType {
    PRIVATE,
    GROUP
}