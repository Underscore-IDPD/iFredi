package com.ifredi.chat.data.repository

import com.google.firebase.auth.FirebaseAuth

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun login(email: String, password: String, callback: (Result<Unit>) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(e)) }
    }

    fun signOut() = auth.signOut()
}