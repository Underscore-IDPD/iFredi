package com.ifredi.chat.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ifredi.chat.data.repository.UserRepository

class IFrediMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val chatId = data["chatId"] ?: return
        
        if (ChatSession.openChatId == chatId) return

        val senderId = data["senderId"].orEmpty()
        val senderName = data["senderName"] ?: "Nuevo mensaje"
        val text = (data["text"] ?: message.notification?.body).orEmpty().ifBlank { "Imagen" }

        NotificationHelper.showMessageNotification(
            context = applicationContext,
            chatId = chatId,
            senderId = senderId,
            senderName = senderName,
            messageText = text
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseAuth.getInstance().currentUser?.uid?.let { UserRepository().saveFcmToken(it, token) }
    }
}