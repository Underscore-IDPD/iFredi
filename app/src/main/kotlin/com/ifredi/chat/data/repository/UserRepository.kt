package com.ifredi.chat.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.auth.FirebaseAuth
import com.ifredi.chat.data.User

class UserRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "UserRepository"
        private const val COLLECTION_USERS = "users"
    }

    fun registerUser(
        email: String,
        password: String,
        displayName: String,
        callback: (Result<Unit>) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    callback(Result.failure(IllegalStateException("Usuario nulo tras el registro")))
                    return@addOnSuccessListener
                }
                val now = System.currentTimeMillis()
                val user = User(
                    id = firebaseUser.uid,
                    email = email,
                    displayName = displayName,
                    createdAt = now,
                    updatedAt = now
                )
                db.collection(COLLECTION_USERS).document(firebaseUser.uid).set(user)
                    .addOnSuccessListener { callback(Result.success(Unit)) }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "registerUser set error", e)
                        // Si falla el perfil, se borra la cuenta para no dejarla a medias
                        firebaseUser.delete().addOnCompleteListener { callback(Result.failure(e)) }
                    }
            }
            .addOnFailureListener { e -> callback(Result.failure(e)) }
    }

    fun ensureUserDocument(userId: String, email: String, callback: (Boolean) -> Unit) {
        val ref = db.collection(COLLECTION_USERS).document(userId)
        ref.get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) { callback(true); return@addOnSuccessListener }
                val now = System.currentTimeMillis()
                val user = User(
                    id = userId, email = email,
                    displayName = email.substringBefore("@"),
                    createdAt = now, updatedAt = now
                )
                ref.set(user)
                    .addOnSuccessListener { callback(true) }
                    .addOnFailureListener { e -> Log.e(TAG, "ensureUserDocument set error", e); callback(false) }
            }
            .addOnFailureListener { e -> Log.e(TAG, "ensureUserDocument get error", e); callback(false) }
    }

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

    fun observeUserPresence(userId: String, callback: (User?) -> Unit): ListenerRegistration {
        return db.collection(COLLECTION_USERS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    callback(null)
                    return@addSnapshotListener
                }
                callback(snapshot?.toObject(User::class.java))
            }
    }

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