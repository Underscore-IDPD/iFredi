package com.ifredi.chat.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FieldValue
import com.ifredi.chat.data.Chat
import com.ifredi.chat.data.ChatType
import com.ifredi.chat.data.Message
import com.ifredi.chat.data.MessageStatus
import com.ifredi.chat.data.User
import java.util.UUID

class ChatRepository {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    companion object {
        private const val TAG = "ChatRepository"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_CHATS = "chats"
        private const val COLLECTION_MESSAGES = "messages"

    }

    /**
     * Mensajes
     */
    private fun messagesRef(chatId: String) =
        db.collection(COLLECTION_CHATS)
            .document(chatId)
            .collection(COLLECTION_MESSAGES)

    // Solo mensajes desde sinceMillis (ayer 00:00). Funciona offline desde el caché de Firestore.
    fun getRecentMessagesRealtime(
        chatId: String,
        sinceMillis: Long,
        callback: (List<Message>) -> Unit
    ): ListenerRegistration {
        return messagesRef(chatId)
            .whereGreaterThanOrEqualTo("timestamp", sinceMillis)
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getRecentMessagesRealtime error", error)
                    callback(emptyList())
                    return@addSnapshotListener
                }
                callback(
                    snapshot?.documents
                        ?.mapNotNull { it.toObject(Message::class.java) }
                        ?: emptyList()
                )
            }
    }

    // Página de mensajes anteriores. Solo servidor: sin conexión falla y devuelve null.
    fun getOlderMessages(
        chatId: String,
        beforeMillis: Long,
        limit: Long,
        callback: (List<Message>?) -> Unit
    ) {
        messagesRef(chatId)
            .whereLessThan("timestamp", beforeMillis)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                callback(snapshot.documents.mapNotNull { it.toObject(Message::class.java) })
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "getOlderMessages error", e)
                callback(null)
            }
    }

    // Envía un mensaje y actualiza la metadata del chat
    fun sendMessage(
        chatId: String,
        message: Message,
        recipientId: String?,
        callback: (Boolean) -> Unit
    ) {
        val messageRef = db.collection(COLLECTION_CHATS)
            .document(chatId)
            .collection(COLLECTION_MESSAGES)
            .document(message.id)

        val chatRef = db.collection(COLLECTION_CHATS).document(chatId)
        val preview = if (message.isImage()) "Imagen" else message.text

        val chatUpdate = mutableMapOf<String, Any>(
            "lastMessage" to preview,
            "lastMessageTime" to message.timestamp,
            "lastMessageSenderId" to message.senderId,
            "updatedAt" to message.timestamp
        )
        if (!recipientId.isNullOrEmpty()) {
            // La clave con punto actualiza solo el contador de ese usuario
            chatUpdate["unreadCounts.$recipientId"] = FieldValue.increment(1)
        }

        val batch = db.batch()
        batch.set(messageRef, message)
        batch.update(chatRef, chatUpdate)

        batch.commit()
            .addOnSuccessListener {
                messageRef.update("status", MessageStatus.SENT.name)
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "sendMessage error", e)
                callback(false)
            }
    }

    fun resetUnread(chatId: String, userId: String) {
        db.collection(COLLECTION_CHATS)
            .document(chatId)
            .update("unreadCounts.$userId", 0)
            .addOnFailureListener { e -> Log.e(TAG, "resetUnread error", e) }
    }

    // Marca un mensaje como leído y actualiza su estado a "READ"
    fun markMessageAsRead(chatId: String, messageId: String) {
        db.collection(COLLECTION_CHATS)
            .document(chatId)
            .collection(COLLECTION_MESSAGES)
            .document(messageId)
            .update(
                mapOf(
                    "isRead" to true,
                    "readAt" to System.currentTimeMillis(),
                    "status" to "READ"
                )
            )
            .addOnFailureListener { e -> Log.e(TAG, "markMessageAsRead error", e) }
    }

    // Elimina un mensaje del chat
    fun deleteMessage(chatId: String, messageId: String, callback: (Boolean) -> Unit) {

        db.collection(COLLECTION_CHATS)
            .document(chatId)
            .collection(COLLECTION_MESSAGES)
            .document(messageId)
            .delete()
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "deleteMessage error", e)
                callback(false)
            }
    }

    // Recalcula el resumen del chat (lastMessage, hora, remitente) con el mensaje más reciente que quede
    fun refreshLastMessage(chatId: String) {
        val chatRef = db.collection(COLLECTION_CHATS).document(chatId)

        messagesRef(chatId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val last = snapshot.documents.firstOrNull()?.toObject(Message::class.java)

                val update: Map<String, Any> = if (last == null) {
                    // No queda ningún mensaje: se limpia el resumen pero se conserva
                    // lastMessageTime para que el chat no se hunda al final de la lista
                    mapOf(
                        "lastMessage" to "",
                        "lastMessageSenderId" to FieldValue.delete()
                    )
                } else {
                    mapOf(
                        "lastMessage" to if (last.isImage()) "Imagen" else last.text,
                        "lastMessageTime" to last.timestamp,
                        "lastMessageSenderId" to last.senderId
                    )
                }

                chatRef.update(update)
                    .addOnFailureListener { e -> Log.e(TAG, "refreshLastMessage update error", e) }
            }
            .addOnFailureListener { e -> Log.e(TAG, "refreshLastMessage query error", e) }
    }

    /**
     * Imágenes
     */

    // Sube una imagen a Firebase Storage y retorna la URL de descarga
    fun uploadImage(imageUri: Uri, chatId: String, callback: (String?) -> Unit) {
        val fileName = "${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child("chats/$chatId/images/$fileName")

        ref.putFile(imageUri)
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { url -> callback(url.toString()) }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "uploadImage getDownloadUrl error", e)
                        callback(null)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "uploadImage putFile error", e)
                callback(null)
            }
    }

    /**
     * Chats
     */

    // Retorna los chats en los que participa el usuario, ordenados por lastMessageTime descendente
    fun getUserChats(userId: String, callback: (List<Chat>) -> Unit): ListenerRegistration {
        return db.collection(COLLECTION_CHATS)
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getUserChats error", error)
                    callback(emptyList())
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents
                    ?.mapNotNull { it.toObject(Chat::class.java) }
                    ?.sortedByDescending { it.lastMessageTime }
                    ?: emptyList()
                callback(chats)
            }
    }

    // Crea un chat privado entre dos usuarios si no existe, y retorna el chatId
    fun createPrivateChat(
        currentUserId: String,
        otherUserId: String,
        callback: (String?) -> Unit
    ) {
        val chatId = listOf(currentUserId, otherUserId).sorted().joinToString("_")
        val chatRef = db.collection(COLLECTION_CHATS).document(chatId)

        chatRef.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    callback(chatId)
                    return@addOnSuccessListener
                }

                // Se necesitan los nombres para poblar participantNames cacheado.
                db.collection(COLLECTION_USERS).document(otherUserId).get()
                    .addOnSuccessListener { otherUserDoc ->
                        val otherUser = otherUserDoc.toObject(User::class.java)

                        db.collection(COLLECTION_USERS).document(currentUserId).get()
                            .addOnSuccessListener { currentUserDoc ->
                                val currentUser = currentUserDoc.toObject(User::class.java)
                                val now = System.currentTimeMillis()

                                val orderedIds = listOf(currentUserId, otherUserId).sorted()
                                val namesById = mapOf(
                                    currentUserId to (currentUser?.displayName ?: ""),
                                    otherUserId to (otherUser?.displayName ?: "")
                                )

                                val chat = Chat(
                                    id = chatId,
                                    participants = orderedIds,
                                    participantNames = orderedIds.map { namesById[it] ?: "" },
                                    type = ChatType.PRIVATE,
                                    name = null,
                                    lastMessage = "",
                                    lastMessageTime = now,
                                    unreadCount = 0,
                                    createdAt = now,
                                    updatedAt = now
                                )

                                chatRef.set(chat)
                                    .addOnSuccessListener { callback(chatId) }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "createPrivateChat set error", e)
                                        callback(null)
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "createPrivateChat currentUser fetch error", e)
                                callback(null)
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "createPrivateChat otherUser fetch error", e)
                        callback(null)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "createPrivateChat chatRef.get error", e)
                callback(null)
            }
    }

    // Retorna un chat específico en tiempo real
    fun getChatRealtime(chatId: String, callback: (Chat?) -> Unit): ListenerRegistration {
        return db.collection(COLLECTION_CHATS)
            .document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getChatRealtime error", error)
                    callback(null)
                    return@addSnapshotListener
                }
                callback(snapshot?.toObject(Chat::class.java))
            }
    }

    /**
     * Estado
     */

    // Marca si el usuario está escribiendo en el chat actual
    fun updateTypingStatus(chatId: String, userId: String, isTyping: Boolean) {
        db.collection(COLLECTION_CHATS)
            .document(chatId)
            .collection("typingStatus")
            .document(userId)
            .set(mapOf("isTyping" to isTyping, "updatedAt" to System.currentTimeMillis()))
            .addOnFailureListener { e -> Log.e(TAG, "updateTypingStatus error", e) }
    }

    // Observa en tiempo real si un usuario está escribiendo en un chat específico
    fun observeTypingStatus(
        chatId: String,
        userId: String,
        callback: (Boolean) -> Unit
    ): ListenerRegistration {
        return db.collection(COLLECTION_CHATS)
            .document(chatId)
            .collection("typingStatus")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeTypingStatus error", error)
                    callback(false)
                    return@addSnapshotListener
                }
                val isTyping = snapshot?.getBoolean("isTyping") ?: false
                callback(isTyping)
            }
    }


}