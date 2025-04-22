// app/src/main/java/com/example/bestsplit/data/repository/AuthRepository.kt
package com.example.bestsplit.data.repository

import android.util.Log
import com.example.bestsplit.data.model.AuthState
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState

    init {
        // Initialize auth state
        auth.currentUser?.let {
            _authState.value = AuthState.Authenticated(it)
        } ?: run {
            _authState.value = AuthState.Unauthenticated
        }

        // Listen for auth changes
        auth.addAuthStateListener { firebaseAuth ->
            firebaseAuth.currentUser?.let {
                _authState.value = AuthState.Authenticated(it)
            } ?: run {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    suspend fun signUp(email: String, password: String): FirebaseUser? {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign up failed", e)
            null
        }
    }

    suspend fun signIn(email: String, password: String): FirebaseUser? {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign in failed", e)
            null
        }
    }

    fun signOut() {
        auth.signOut()
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun signInWithCredential(
        credential: AuthCredential,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onError(task.exception?.message ?: "Authentication failed")
                }
            }
    }
}