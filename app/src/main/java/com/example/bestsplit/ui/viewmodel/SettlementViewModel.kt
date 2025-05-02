package com.example.bestsplit.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bestsplit.data.database.AppDatabase
import com.example.bestsplit.data.entity.Settlement
import com.example.bestsplit.data.repository.SettlementRepository
import com.example.bestsplit.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettlementViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: SettlementRepository
    private val userRepository: UserRepository

    sealed class SettlementState {
        object Idle : SettlementState()
        object Loading : SettlementState()
        data class Success(val settlementId: Long) : SettlementState()
        data class Error(val message: String) : SettlementState()
    }

    private val _settlementState = MutableStateFlow<SettlementState>(SettlementState.Idle)
    val settlementState: StateFlow<SettlementState> = _settlementState.asStateFlow()

    init {
        val settlementDao = AppDatabase.getDatabase(application).settlementDao()
        repository = SettlementRepository(settlementDao)
        userRepository = UserRepository()
    }

    fun initializeRepository() {
        repository.initialize()
    }

    fun getSettlementsForGroup(groupId: Long): Flow<List<Settlement>> {
        return repository.getSettlementsForGroup(groupId)
    }

    fun syncSettlementsForGroup(groupId: Long) {
        viewModelScope.launch {
            repository.syncSettlementsForGroup(groupId)
        }
    }

    fun addSettlement(
        groupId: Long,
        fromUserId: String,
        toUserId: String,
        amount: Double,
        description: String = ""
    ) {
        viewModelScope.launch {
            try {
                // Set loading state first
                _settlementState.value = SettlementState.Loading

                Log.d("SettlementViewModel", "Adding settlement: $fromUserId -> $toUserId, $amount")

                val settlement = Settlement(
                    groupId = groupId,
                    fromUserId = fromUserId,
                    toUserId = toUserId,
                    amount = amount,
                    description = description,
                    createdAt = System.currentTimeMillis()
                )

                val id = repository.addSettlement(settlement)

                // Log and set success state
                Log.d("SettlementViewModel", "Settlement added successfully with ID: $id")
                _settlementState.value = SettlementState.Success(id)
            } catch (e: Exception) {
                Log.e("SettlementViewModel", "Error adding settlement", e)
                _settlementState.value = SettlementState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetSettlementState() {
        Log.d("SettlementViewModel", "Resetting settlement state to Idle")
        _settlementState.value = SettlementState.Idle
    }

    // Get user details for display
    suspend fun getUserDetails(userId: String): UserRepository.User? {
        return userRepository.getUserById(userId)
    }
}