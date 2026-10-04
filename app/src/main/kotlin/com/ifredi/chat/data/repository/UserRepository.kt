package com.ifredi.chat.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ifredi.chat.data.User

class UserRepository {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "UserRepository"
        private const val COLLECTION_USERS = "users"
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