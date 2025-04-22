// app/src/main/java/com/example/bestsplit/data/repository/FriendsRepository.kt
package com.example.bestsplit.data.repository

import android.util.Log
import com.example.bestsplit.Friend
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FriendsRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())

    fun getFriends(): Flow<List<Friend>> {
        val currentUserId = auth.currentUser?.uid ?: return _friends.asStateFlow()

        firestore.collection("users")
            .document(currentUserId)
            .collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                val friendsList = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Friend::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                _friends.value = friendsList
            }

        return _friends.asStateFlow()
    }

    // Update in FriendsRepository.kt
    suspend fun addFriend(email: String): Boolean {
        val currentUserId = auth.currentUser?.uid
        Log.d("FriendsRepository", "Current user ID: $currentUserId")

        if (currentUserId == null) {
            Log.e("FriendsRepository", "Failed to add friend: No current user")
            return false
        }

        val currentUserEmail = auth.currentUser?.email
        Log.d("FriendsRepository", "Current user email: $currentUserEmail")

        if (email == currentUserEmail) {
            Log.e("FriendsRepository", "Can't add yourself as friend")
            return false
        }

        try {
            // Look up the user by email
            Log.d("FriendsRepository", "Looking for user with email: $email")
            val userQuery = firestore.collection("users")
                .whereEqualTo("email", email)
                .get()
                .await()

            val userDoc = userQuery.documents.firstOrNull()
            if (userDoc == null) {
                Log.e("FriendsRepository", "No user found with email: $email")
                return false
            }

            val friendId = userDoc.id
            val friendName = userDoc.getString("name") ?: "Unknown"
            Log.d("FriendsRepository", "Found user: $friendName with ID: $friendId")

            // Add friend to current user's friends collection
            val friend = Friend(id = friendId, name = friendName, email = email)
            firestore.collection("users")
                .document(currentUserId)
                .collection("friends")
                .document(friendId)
                .set(friend)
                .await()

            Log.d("FriendsRepository", "Successfully added friend")
            return true
        } catch (e: Exception) {
            Log.e("FriendsRepository", "Error adding friend", e)
            return false
        }
    }
}