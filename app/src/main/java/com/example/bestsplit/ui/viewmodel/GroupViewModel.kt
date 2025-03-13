// app/src/main/java/com/example/bestsplit/ui/viewmodel/GroupViewModel.kt
package com.example.bestsplit.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bestsplit.data.database.AppDatabase
import com.example.bestsplit.data.entity.Group
import com.example.bestsplit.data.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class GroupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: GroupRepository
    val allGroups: Flow<List<Group>>

    init {
        val groupDao = AppDatabase.getDatabase(application).groupDao()
        repository = GroupRepository(groupDao)
        allGroups = repository.allGroups
    }

    fun insertGroup(name: String, description: String) {
        viewModelScope.launch {
            val group = Group(name = name, description = description)
            repository.insertGroup(group)
        }
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
}