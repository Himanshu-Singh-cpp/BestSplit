package com.example.bestsplit.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bestsplit.data.database.AppDatabase
import com.example.bestsplit.data.entity.Group
import com.example.bestsplit.data.repository.FriendsRepository
import com.example.bestsplit.data.repository.GroupRepository
import com.example.bestsplit.Friend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: GroupRepository
    private val friendsRepository: FriendsRepository
    val allGroups: Flow<List<Group>>

    private val _selectedFriends = MutableStateFlow<List<Friend>>(emptyList())
    val selectedFriends: StateFlow<List<Friend>> = _selectedFriends.asStateFlow()

    init {
        val groupDao = AppDatabase.getDatabase(application).groupDao()
        repository = GroupRepository(groupDao)
        friendsRepository = FriendsRepository()
        allGroups = repository.allGroups

    }

    fun insertGroup(name: String, description: String, members: List<String> = emptyList()) {
        viewModelScope.launch {
            val group = Group(name = name, description = description)
            repository.insertGroup(group, members)
        }
    }

    fun toggleFriendSelection(friend: Friend) {
        val currentList = _selectedFriends.value
        _selectedFriends.value = if (friend in currentList) {
            currentList - friend
        } else {
            currentList + friend
        }
    }

    fun clearSelectedFriends() {
        _selectedFriends.value = emptyList()
    }

    suspend fun getGroupById(id: Long): Group? {
        return repository.getGroupById(id)
    }

    fun updateGroup(group: Group) {
        viewModelScope.launch {
            repository.updateGroup(group)
        }
    }

    fun deleteGroup(group: Group) {
        viewModelScope.launch {
            repository.deleteGroup(group)
        }
    }

    fun addMemberToGroup(groupId: Long, userId: String) {
        viewModelScope.launch {
            repository.addMemberToGroup(groupId, userId)
        }
    }

    fun removeMemberFromGroup(groupId: Long, userId: String) {
        viewModelScope.launch {
            repository.removeMemberFromGroup(groupId, userId)
        }
    }

    fun refreshGroups() {
        viewModelScope.launch {
            Log.d("GroupViewModel", "Manually refreshing groups from cloud")
            repository.syncFromCloud()
        }
    }
}