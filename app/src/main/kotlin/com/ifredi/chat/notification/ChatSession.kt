package com.ifredi.chat.notification

object ChatSession {
    @Volatile
    var openChatId: String? = null
}