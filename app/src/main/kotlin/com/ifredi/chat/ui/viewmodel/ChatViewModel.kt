package com.ifredi.chat.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import com.ifredi.chat.data.Chat
import com.ifredi.chat.data.Message
import com.ifredi.chat.data.MessageStatus
import com.ifredi.chat.data.MessageType
import com.ifredi.chat.data.repository.ChatRepository
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    companion object {
        private const val MAX_MESSAGE_LENGTH = 4000
        private const val TYPING_DEBOUNCE_MS = 3000L
    }

    private val _messages = MutableLiveData<List<Message>>()
    val messages: LiveData<List<Message>> = _messages

    private val _isUserTyping = MutableLiveData<Boolean>()
    val isUserTyping: LiveData<Boolean> = _isUserTyping

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _chat = MutableLiveData<Chat>()
    val chat: LiveData<Chat> = _chat

    private val _sendingMessage = MutableLiveData<Message?>()
    val sendingMessage: LiveData<Message?> = _sendingMessage

    private var currentUserId = ""
    private var chatId = ""
    private var chatPartnerId = ""
    private var typingTimer: Timer? = null

    private var messagesListener: ListenerRegistration? = null
    private var chatListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null

    // Inicializa el ViewModel con el ID del usuario actual y el ID del chat
    fun initialize(userId: String, chatId: String) {
        this.currentUserId = userId
        this.chatId = chatId
        _isLoading.value = true

        messagesListener = repository.getMessagesRealtime(chatId) { messageList ->
            _isLoading.value = false
            _messages.value = messageList
        }

        chatListener = repository.getChatRealtime(chatId) { chatData ->
            if (chatData != null) {
                _chat.value = chatData
                chatPartnerId = chatData.getOtherUserId(userId) ?: ""
            }
        }

        if (chatPartnerId.isNotEmpty()) {
            observePartnerTyping()
        }
    }

    private fun observePartnerTyping() {
        typingListener?.remove()
        typingListener = repository.observeTypingStatus(chatId, chatPartnerId) { isTyping ->
            _isUserTyping.value = isTyping
        }
    }

    /**
     * Mensajería
     */

    // Envía un mensaje de texto o imagen al chat
    fun sendMessage(text: String, imageUrl: String? = null) {
        val trimmedText = text.trim()

        if (trimmedText.isEmpty() && imageUrl == null) {
            _errorMessage.value = "No puedes enviar un mensaje vacío"
            return
        }
        if (trimmedText.length > MAX_MESSAGE_LENGTH) {
            _errorMessage.value = "El mensaje supera los $MAX_MESSAGE_LENGTH caracteres"
            return
        }
        if (currentUserId.isEmpty()) {
            _errorMessage.value = "Debes iniciar sesión para enviar mensajes"
            return
        }

        val message = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = currentUserId,
            text = trimmedText,
            imageUrl = imageUrl,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            status = MessageStatus.PENDING,
            type = if (imageUrl != null) MessageType.IMAGE else MessageType.TEXT
        )

        _sendingMessage.value = message

        repository.sendMessage(chatId, message) { success ->
            _sendingMessage.value = null
            if (!success) {
                _errorMessage.value = "No se pudo enviar el mensaje"
            }
        }
    }

    // Notifica que el usuario está escribiendo, con debounce de 3s
    fun sendTypingIndicator() {
        if (currentUserId.isEmpty() || chatId.isEmpty()) return

        repository.updateTypingStatus(chatId, currentUserId, true)

        typingTimer?.cancel()
        typingTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    repository.updateTypingStatus(chatId, currentUserId, false)
                }
            }, TYPING_DEBOUNCE_MS)
        }
    }

    // Elimina un mensaje, validando primero que pertenezca al usuario actual
    fun deleteMessage(message: Message) {
        if (!message.isOwn(currentUserId)) {
            _errorMessage.value = "Solo puedes eliminar tus propios mensajes"
            return
        }
        repository.deleteMessage(chatId, message.id) { success ->
            if (!success) {
                _errorMessage.value = "No se pudo eliminar el mensaje"
            }
        }
    }

    // Marca los mensajes como leídos si no son del usuario actual
    fun markChatAsRead() {
        val unreadMessages = _messages.value.orEmpty().filter {
            !it.isRead && it.senderId != currentUserId
        }
        unreadMessages.forEach { message ->
            repository.markMessageAsRead(chatId, message.id)
        }
    }

    /**
     * Imagenes
     */

    // Sube una imagen al chat y devuelve la URL de la imagen subida a través del callback
    fun uploadImage(imageUri: android.net.Uri, callback: (String?) -> Unit) {
        _isLoading.value = true
        repository.uploadImage(imageUri, chatId) { url ->
            _isLoading.value = false
            if (url == null) {
                _errorMessage.value = "No se pudo subir la imagen"
            }
            callback(url)
        }
    }

    /**
     * Errores
     */

    // Limpia el mensaje de error actual
    fun clearError() {
        _errorMessage.value = ""
    }

    /**
     * Limpieza
     */

    // Limpia los recursos y actualiza el estado en línea del usuario cuando el ViewModel se destruye
    override fun onCleared() {
        super.onCleared()
        messagesListener?.remove()
        chatListener?.remove()
        typingListener?.remove()
        typingTimer?.cancel()
        if (currentUserId.isNotEmpty()) {
            repository.updateUserOnlineStatus(currentUserId, false)
        }
    }
}