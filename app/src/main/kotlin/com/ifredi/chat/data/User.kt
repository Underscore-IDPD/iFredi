package com.ifredi.chat.data

import com.google.firebase.firestore.PropertyName

data class User(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val profileImageUrl: String? = null,
    val status: String = "",
    val lastSeen: Long = 0L,
    val phoneNumber: String? = null,
    val bio: String? = null,
    @get:PropertyName("isOnline")
    @set:PropertyName("isOnline")
    var isOnline: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    // Verifica si el usuario está actualmente en línea
    fun isActive(): Boolean = isOnline && System.currentTimeMillis() - lastSeen < 70_000

    // Retorna las iniciales del nombre para usar en el avatar
    fun getInitials(): String {
        if (displayName.isBlank()) return "?"
        return displayName
            .trim()
            .split(Regex("\\s+"))
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
    }
}