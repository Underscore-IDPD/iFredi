package com.ifredi.chat.notification

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class IFrediMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "IFrediMessagingService"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val chatId = data["chatId"] ?: return
        val senderId = data["senderId"] ?: ""
        val senderName = data["senderName"] ?: "Nuevo mensaje"
        val text = data["text"] ?: message.notification?.body ?: ""

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
        Log.d(TAG, "Nuevo token FCM: $token")

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .update("fcmToken", token)
        }
    }
}