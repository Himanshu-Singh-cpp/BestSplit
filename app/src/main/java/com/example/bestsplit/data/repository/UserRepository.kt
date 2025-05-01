package com.example.bestsplit.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val TAG = "UserRepository"
    private val COLLECTION_USERS = "users"

    // Cache to avoid frequent Firestore reads
    private val userCache = mutableMapOf<String, User>()

    data class User(
        val id: String = "",
        val name: String = "",
        val email: String = "",
        val photoUrl: String = "",
        val upiId: String? = null
    )

    suspend fun getUserById(userId: String): User? {
        // Check cache first
        if (userCache.containsKey(userId)) {
            return userCache[userId]
        }

        return try {
            val document = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                val name = document.getString("name") ?: "Unknown"
                val email = document.getString("email") ?: ""
                val photoUrl = document.getString("photoUrl") ?: ""
                val upiId = document.getString("upiId")

                val user = User(userId, name, email, photoUrl, upiId)
                userCache[userId] = user
                user
            } else {
                Log.d(TAG, "No user found with ID: $userId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user data", e)
            null
        }
    }

    suspend fun saveUser(user: User) {
        try {
            val userId = user.id
            val document = firestore.collection(COLLECTION_USERS).document(userId)
            document.set(
                mapOf(
                    "name" to user.name,
                    "email" to user.email,
                    "photoUrl" to user.photoUrl,
                    "upiId" to user.upiId
                )
            ).await()
            userCache[userId] = user
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user data", e)
        }
    }

    fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: ""
    }

    // Clear cache when needed
    fun clearCache() {
        userCache.clear()
    }
}