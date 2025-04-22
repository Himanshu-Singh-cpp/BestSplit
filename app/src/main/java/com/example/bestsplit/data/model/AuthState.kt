// app/src/main/java/com/example/bestsplit/data/model/AuthState.kt
package com.example.bestsplit.data.model

import com.google.firebase.auth.FirebaseUser

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    object Unauthenticated : AuthState()
}