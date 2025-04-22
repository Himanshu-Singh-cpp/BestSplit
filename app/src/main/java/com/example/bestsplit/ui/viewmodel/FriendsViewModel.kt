// app/src/main/java/com/example/bestsplit/ui/viewmodel/FriendsViewModel.kt
package com.example.bestsplit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bestsplit.Friend
import com.example.bestsplit.data.repository.FriendsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FriendsViewModel : ViewModel() {
    private val repository = FriendsRepository()

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    init {
        loadFriends()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            repository.getFriends().collect { friendsList ->
                _friends.value = friendsList
            }
        }
    }

    // Update in FriendsViewModel
    fun addFriend(email: String, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val success = repository.addFriend(email)
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }
}