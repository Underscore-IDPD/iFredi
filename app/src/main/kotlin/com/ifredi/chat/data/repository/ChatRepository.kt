package com.ifredi.chat.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.ifredi.chat.data.Chat
import com.ifredi.chat.data.ChatType
import com.ifredi.chat.data.Message
import com.ifredi.chat.data.User
import java.util.UUID

class ChatRepository {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "ChatRepository"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_CHATS = "chats"
        private const val COLLECTION_MESSAGES = "messages"
        private const val MESSAGES_LIMIT = 100L
    }

    /**
     * Mensajes
     */

    // Escucha en tiempo real los mensajes de un chat, ordenados por timestamp descendente
    fun getMessagesRealtime(
        chatId: String,
        callback: (List<Message>) -> Unit
    ): ListenerRegistration {
        return db.collection(COLLECTION_CHATS)
            .document(chatId)
            .collection(COLLECTION_MESSAGES)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(MESSAGES_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getMessagesRealtime error", error)
                    callback(emptyList())
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents
                    ?.mapNotNull { it.toObject(Message::class.java) }
                    ?.sortedBy { it.timestamp }
                    ?: emptyList()
                callback(messages)
            }
    }

    // Envía un mensaje y actualiza la metadata del chat
    fun sendMessage(chatId: String, message: Message, callback: (Boolean) -> Unit) {
        val messageRef = db.collection(COLLECTION_CHATS)
            .document(chatId)
            .collection(COLLECTION_MESSAGES)
            .document(message.id)

        val chatRef = db.collection(COLLECTION_CHATS).document(chatId)

        val preview = if (message.isImage()) "Imagen" else message.text

        val batch = db.batch()
        batch.set(messageRef, message)
        batch.update(
            chatRef,
            mapOf(
                "lastMessage" to preview,
                "lastMessageTime" to message.timestamp,
                "lastMessageSenderId" to message.senderId,
                "updatedAt" to message.timestamp
            )
        )

        batch.commit()
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "sendMessage error", e)
                callback(false)
            }
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
            .orderBy("lastMessageTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getUserChats error", error)
                    callback(emptyList())
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents
                    ?.mapNotNull { it.toObject(Chat::class.java) }
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

    // Marca si el usuario está en línea o no, y actualiza la última vez que estuvo activo
    fun updateUserOnlineStatus(userId: String, isOnline: Boolean) {
        db.collection(COLLECTION_USERS)
            .document(userId)
            .update(
                mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to System.currentTimeMillis()
                )
            )
            .addOnFailureListener { e -> Log.e(TAG, "updateUserOnlineStatus error", e) }
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

    /**
     * Busqueda
     */

    // Busca usuarios por nombre o correo electrónico, retornando un máximo de 10 resultados
    fun searchUsers(query: String, callback: (List<User>) -> Unit) {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) {
            callback(emptyList())
            return
        }

        db.collection(COLLECTION_USERS)
            .orderBy("displayName")
            .get()
            .addOnSuccessListener { snapshot ->
                val results = snapshot.documents
                    .mapNotNull { it.toObject(User::class.java) }
                    .filter {
                        it.displayName.lowercase().contains(normalized) ||
                                it.email.lowercase().contains(normalized)
                    }
                    .take(10)
                callback(results)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "searchUsers error", e)
                callback(emptyList())
            }
    }
}