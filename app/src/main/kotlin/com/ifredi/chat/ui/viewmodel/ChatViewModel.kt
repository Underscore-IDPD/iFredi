package com.ifredi.chat.ui.viewmodel

import android.icu.util.Calendar
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import com.ifredi.chat.data.Chat
import com.ifredi.chat.data.Message
import com.ifredi.chat.data.MessageStatus
import com.ifredi.chat.data.MessageType
import com.ifredi.chat.data.User
import com.ifredi.chat.data.repository.ChatRepository
import com.ifredi.chat.data.repository.UserRepository
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.UUID

class ChatViewModel : ViewModel() {

    private val _partnerUser = MutableLiveData<User?>()
    val partnerUser: LiveData<User?> = _partnerUser

    private var presenceListener: ListenerRegistration? = null

    private val chatRepository = ChatRepository()
    private val userRepository = UserRepository()

    companion object {
        private const val MAX_MESSAGE_LENGTH = 4000
        private const val TYPING_DEBOUNCE_MS = 3000L      // sin teclear 3s -> false
        private const val TYPING_THROTTLE_MS = 2000L      // heartbeat máximo cada 2s
        private const val PARTNER_TYPING_TTL_MS = 5000L   // sin eventos 5s -> se oculta
        private const val OLDER_PAGE_SIZE = 30L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val partnerTypingExpiry = Runnable { _isUserTyping.value = false }
    private val stopTypingRunnable = Runnable { stopTyping() }
    private var ignoreFirstTypingEvent = true
    private var lastTypingSentAt = 0L



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

    private var currentUserId = ""
    private var chatId = ""
    private var chatPartnerId = ""
    private var messagesListener: ListenerRegistration? = null
    private var chatListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null

    private var windowStart = 0L
    private var recentMessages: List<Message> = emptyList()
    private val olderMessages = mutableListOf<Message>()
    private var isLoadingOlder = false

    private val _canLoadOlder = MutableLiveData(true)
    val canLoadOlder: LiveData<Boolean> = _canLoadOlder

    private val _loadingOlder = MutableLiveData(false)
    val loadingOlder: LiveData<Boolean> = _loadingOlder

    private fun startOfYesterday(): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun publishMessages() {
        // recentMessages primero, para que la versión reciente gane si hay duplicados
        _messages.value = (recentMessages + olderMessages)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
    }

    fun initialize(userId: String, chatId: String) {
        this.currentUserId = userId
        this.chatId = chatId
        _isLoading.value = true

        windowStart = startOfYesterday()
        messagesListener = chatRepository.getRecentMessagesRealtime(chatId, windowStart) { list ->
            _isLoading.value = false
            recentMessages = list
            publishMessages()

            list.forEach { message ->
                if (message.senderId != currentUserId && !message.isRead) {
                    chatRepository.markMessageAsRead(chatId, message.id)
                }
            }
        }

        chatListener = chatRepository.getChatRealtime(chatId) { chatData ->
            if (chatData != null) {
                _chat.value = chatData
                val newPartnerId = chatData.getOtherUserId(userId) ?: ""

                // Only attach observers if the partner ID is found and changed/initialized
                if (chatPartnerId != newPartnerId) {
                    chatPartnerId = newPartnerId
                    if (chatPartnerId.isNotEmpty()) {
                        observePartnerTyping()
                        observePartnerPresence() // Start listening to their user document
                    }
                }
            }
        }
    }
    private fun observePartnerTyping() {
        typingListener?.remove()
        mainHandler.removeCallbacks(partnerTypingExpiry)
        _isUserTyping.value = false
        ignoreFirstTypingEvent = true

        typingListener = chatRepository.observeTypingStatus(chatId, chatPartnerId) { isTyping ->
            if (ignoreFirstTypingEvent) {
                // El estado inicial puede ser un true viejo de una sesión muerta
                ignoreFirstTypingEvent = false
            } else {
                mainHandler.removeCallbacks(partnerTypingExpiry)
                _isUserTyping.value = isTyping
                if (isTyping) mainHandler.postDelayed(partnerTypingExpiry, PARTNER_TYPING_TTL_MS)
            }
        }
    }

    private fun observePartnerPresence() {
        presenceListener?.remove()
        presenceListener = userRepository.observeUserPresence(chatPartnerId) { user ->
            _partnerUser.value = user
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

        chatRepository.sendMessage(chatId, message) { success ->
            if (!success) {
                _errorMessage.value = "No se pudo enviar el mensaje"
            }
        }
    }

    fun loadOlderMessages() {
        if (isLoadingOlder || _canLoadOlder.value != true) return
        isLoadingOlder = true
        _loadingOlder.value = true

        val before = olderMessages.minOfOrNull { it.timestamp } ?: windowStart
        chatRepository.getOlderMessages(chatId, before, OLDER_PAGE_SIZE) { page ->
            isLoadingOlder = false
            _loadingOlder.value = false
            if (page == null) {
                _errorMessage.value = "Necesitas conexión para ver mensajes anteriores"
            } else {
                olderMessages.addAll(page)
                if (page.size < OLDER_PAGE_SIZE) _canLoadOlder.value = false
                publishMessages()
            }
        }
    }


    // Notifica que el usuario está escribiendo, con debounce de 3s
    fun sendTypingIndicator() {
        if (currentUserId.isEmpty() || chatId.isEmpty()) return

        val now = SystemClock.elapsedRealtime()
        if (lastTypingSentAt == 0L || now - lastTypingSentAt >= TYPING_THROTTLE_MS) {
            lastTypingSentAt = now
            chatRepository.updateTypingStatus(chatId, currentUserId, true)
        }
        mainHandler.removeCallbacks(stopTypingRunnable)
        mainHandler.postDelayed(stopTypingRunnable, TYPING_DEBOUNCE_MS)
    }

    fun stopTyping() {
        mainHandler.removeCallbacks(stopTypingRunnable)
        if (lastTypingSentAt != 0L) {
            lastTypingSentAt = 0L
            chatRepository.updateTypingStatus(chatId, currentUserId, false)
        }
    }

    // Elimina un mensaje, validando primero que pertenezca al usuario actual
    fun deleteMessage(message: Message) {
        if (!message.isOwn(currentUserId)) {
            _errorMessage.value = "Solo puedes eliminar tus propios mensajes"
            return
        }
        chatRepository.deleteMessage(chatId, message.id) { success ->
            if (!success) {
                _errorMessage.value = "No se pudo eliminar el mensaje"
            }
            else if (olderMessages.removeAll { it.id == message.id }) publishMessages()
        }
    }

    /**
     * Imagenes
     */

    // Sube una imagen al chat y devuelve la URL de la imagen subida a través del callback
    fun uploadImage(imageUri: android.net.Uri, callback: (String?) -> Unit) {
        _isLoading.value = true
        chatRepository.uploadImage(imageUri, chatId) { url ->
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
        messagesListener?.remove()
        chatListener?.remove()
        typingListener?.remove()
        presenceListener?.remove() // Prevent memory leaks
        stopTyping()
        mainHandler.removeCallbacks(partnerTypingExpiry)
    }
}