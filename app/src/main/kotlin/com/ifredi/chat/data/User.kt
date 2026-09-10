package com.ifredi.chat.data

data class User(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val profileImageUrl: String? = null,
    val status: String = "",
    val lastSeen: Long = 0L,
    val phoneNumber: String? = null,
    val bio: String? = null,
    val isOnline: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {

    fun isActive(): Boolean = isOnline

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