package com.ifredi.chat

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.ifredi.chat.data.repository.UserRepository

class App : Application(), DefaultLifecycleObserver {

    private val userRepository by lazy { UserRepository() }

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        updateStatus(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        updateStatus(false)
    }

    private fun updateStatus(isOnline: Boolean) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            userRepository.updateUserOnlineStatus(userId, isOnline)
        }
    }
}