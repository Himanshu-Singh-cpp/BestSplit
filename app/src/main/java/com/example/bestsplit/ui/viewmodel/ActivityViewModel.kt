package com.example.bestsplit.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bestsplit.data.database.AppDatabase
import com.example.bestsplit.data.entity.Expense
import com.example.bestsplit.data.entity.Group
import com.example.bestsplit.data.repository.ExpenseRepository
import com.example.bestsplit.data.repository.GroupRepository
import com.example.bestsplit.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Date

class ActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val expenseRepository: ExpenseRepository
    private val groupRepository: GroupRepository
    private val userRepository: UserRepository

    private val _activities = MutableStateFlow<List<ActivityItem>>(emptyList())
    val activities: StateFlow<List<ActivityItem>> = _activities.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        expenseRepository = ExpenseRepository(db.expenseDao())
        groupRepository = GroupRepository(db.groupDao())
        userRepository = UserRepository()

        // Initial load
        loadActivities()

        // Setup automatic refresh after sync
        viewModelScope.launch {
            // First sync data
            syncData()
            // Then load activities with fresh data
            loadActivities()
        }
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                // Wait for 2 minutes before refreshing
                delay(2 * 60 * 1000)
                try {
                    syncData()
                    loadActivities()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Sync data from Firebase
    fun syncData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUserId = userRepository.getCurrentUserId()

                // Do not proceed if not logged in
                if (currentUserId.isEmpty()) {
                    _isLoading.value = false
                    return@launch
                }

                // Sync the groups first
                groupRepository.syncFromCloud()

                // Then get all groups to know which expenses to sync
                val groups = groupRepository.getAllGroupsSync()

                // Only sync for groups the user is a member of
                val userGroupIds = groups
                    .filter { group ->
                        group.members.contains(currentUserId)
                    }
                    .map { it.id }

                if (userGroupIds.isEmpty()) {
                    return@launch
                }

                // Sync expenses for these groups
                expenseRepository.syncAllExpenses(userGroupIds)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Refresh activities from the database after syncing
    fun refreshActivities() {
        viewModelScope.launch {
            syncData()
            loadActivities()
        }
    }

    fun loadActivities() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val currentUserId = userRepository.getCurrentUserId()
                val groups = groupRepository.getAllGroupsSync()

                // Collect expenses from all groups where the user is a member
                val userGroups = groups.filter { group ->
                    group.members.contains(currentUserId)
                }

                val activityItems = mutableListOf<ActivityItem>()

                userGroups.forEach { group ->
                    val expenses = expenseRepository.getExpensesForGroupAsList(group.id)

                    expenses.forEach { expense ->
                        if (expense.paidBy == currentUserId || expense.paidFor.containsKey(
                                currentUserId
                            )
                        ) {
                            val type = when {
                                expense.paidBy == currentUserId -> ActivityType.YOUR_PAYMENT
                                expense.paidFor.containsKey(currentUserId) -> ActivityType.EXPENSE
                                else -> null
                            }

                            if (type == null) return@forEach

                            val amount = when (type) {
                                ActivityType.YOUR_PAYMENT -> {
                                    val yourShare = expense.paidFor[currentUserId] ?: 0.0
                                    expense.amount - yourShare
                                }
                                ActivityType.EXPENSE -> {
                                    expense.paidFor[currentUserId] ?: 0.0
                                }
                                else -> 0.0
                            }

                            if (amount <= 0.0) return@forEach

                            val memberDetails = mutableListOf<String>()
                            val memberIds = expense.paidFor.keys.toList()
                            for (memberId in memberIds) {
                                val user = userRepository.getUserById(memberId)
                                user?.let {
                                    memberDetails.add(if (memberId == currentUserId) "You" else it.name)
                                }
                            }

                            val payerName = if (expense.paidBy == currentUserId) {
                                "You"
                            } else {
                                val payerUser = userRepository.getUserById(expense.paidBy)
                                payerUser?.name ?: "Unknown"
                            }

                            activityItems.add(
                                ActivityItem(
                                    id = expense.id.toString(),
                                    groupId = group.id,
                                    groupName = group.name,
                                    title = expense.description,
                                    amount = amount,
                                    date = Date(expense.createdAt),
                                    participants = memberDetails,
                                    type = type,
                                    payerName = payerName
                                )
                            )
                        }
                    }
                }

                // Sort by date (most recent first)
                activityItems.sortByDescending { it.date }
                _activities.value = activityItems

            } catch (e: Exception) {
                // Handle errors
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

enum class ActivityType {
    EXPENSE, YOUR_PAYMENT
}

data class ActivityItem(
    val id: String,
    val groupId: Long,
    val groupName: String,
    val title: String,
    val amount: Double,
    val date: Date,
    val participants: List<String>,
    val type: ActivityType,
    val payerName: String
)