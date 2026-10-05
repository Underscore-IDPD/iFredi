package com.ifredi.chat

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.ifredi.chat.data.repository.UserRepository
import com.ifredi.chat.notification.NotificationHelper
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

class App : Application(), DefaultLifecycleObserver {

    private val userRepository by lazy { UserRepository() }
    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        NotificationHelper.createNotificationChannel(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        startHeartbeat()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        stopHeartbeat()
        updateStatus(false)
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = applicationScope.launch {
            updateStatus(true)

            while (isActive) {
                delay(30_000.milliseconds)
                updateStatus(true)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun updateStatus(isOnline: Boolean) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            userRepository.updateUserOnlineStatus(userId, isOnline)
        }
    }
}