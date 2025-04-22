// app/src/main/java/com/example/bestsplit/ui/viewmodel/AuthViewModel.kt
package com.example.bestsplit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bestsplit.data.model.AuthState
import com.example.bestsplit.data.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthCredential
import com.example.bestsplit.R

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository()
    val authState: StateFlow<AuthState> = repository.authState

    fun signUp(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val user = repository.signUp(email, password)
            if (user != null) {
                onSuccess()
            } else {
                onError("Sign up failed")
            }
        }
    }

    fun signIn(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val user = repository.signIn(email, password)
            if (user != null) {
                onSuccess()
            } else {
                onError("Sign in failed")
            }
        }
    }

    fun signOut() {
        repository.signOut()
    }

    // Configure Google Sign In
    private lateinit var googleSignInClient: GoogleSignInClient

    fun setupGoogleSignIn(context: Context) {

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    // Remove the current implementation and replace with this:
    fun getGoogleSignInIntent(): Intent {
        // Use the previously initialized googleSignInClient
        if (!::googleSignInClient.isInitialized) {
            throw IllegalStateException("Google Sign-In client not initialized. Call setupGoogleSignIn first.")
        }
        return googleSignInClient.signInIntent
    }

    fun handleGoogleSignInResult(task: Task<GoogleSignInAccount>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            val account = task.getResult(ApiException::class.java)
            // Authenticate with Firebase using Google credentials
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            repository.signInWithCredential(credential, onSuccess, onError)
        } catch (e: ApiException) {
            onError("Google sign in failed: ${e.localizedMessage}")
        }
    }
}