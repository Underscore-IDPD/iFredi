package com.ifredi.chat.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.ifredi.chat.data.Chat
import com.ifredi.chat.data.User
import com.ifredi.chat.data.repository.ChatRepository

class MainViewModel : ViewModel() {

    private val repository = ChatRepository()

    data class ChatTarget(val chatId: String, val partnerId: String, val partnerName: String)

    private val _chats = MutableLiveData<List<Chat>>(emptyList())
    val chats: LiveData<List<Chat>> = _chats

    private val _searchResults = MutableLiveData<List<User>>(emptyList())
    val searchResults: LiveData<List<User>> = _searchResults

    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _openChat = MutableLiveData<ChatTarget?>()
    val openChat: LiveData<ChatTarget?> = _openChat

    private var currentUserId = ""
    private var userReady = false
    private var latestQuery = ""
    private var chatsListener: ListenerRegistration? = null

    fun initialize(userId: String, email: String) {
        if (currentUserId == userId) return
        currentUserId = userId
        _isLoading.value = true

        repository.ensureUserDocument(userId, email) { ok ->
            userReady = ok
            if (ok) {
                repository.updateUserOnlineStatus(userId, true)
            } else {
                _errorMessage.value = "No se pudo cargar tu perfil"
            }
        }

        chatsListener = repository.getUserChats(userId) { list ->
            _isLoading.value = false
            _chats.value = list
        }
    }

    fun setOnline(online: Boolean) {
        if (userReady) repository.updateUserOnlineStatus(currentUserId, online)
    }

    /**
     * Búsqueda
     */

    fun search(query: String) {
        val q = query.trim()
        latestQuery = q
        if (q.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        repository.searchUsers(q) { users ->
            if (q != latestQuery) return@searchUsers // llegó una respuesta vieja
            _searchResults.value = users.filter { it.id != currentUserId }
        }
    }

    fun startChatWith(user: User) {
        repository.createPrivateChat(currentUserId, user.id) { chatId ->
            if (chatId == null) {
                _errorMessage.value = "No se pudo abrir el chat"
            } else {
                _openChat.value = ChatTarget(chatId, user.id, user.displayName)
            }
        }
    }

    fun consumeOpenChat() {
        _openChat.value = null
    }

    fun clearError() {
        _errorMessage.value = ""
    }

    /**
     * Sesión
     */

    fun signOut() {
        setOnline(false)
        userReady = false
        chatsListener?.remove()
        FirebaseAuth.getInstance().signOut()
    }

    override fun onCleared() {
        super.onCleared()
        chatsListener?.remove()
    }
}