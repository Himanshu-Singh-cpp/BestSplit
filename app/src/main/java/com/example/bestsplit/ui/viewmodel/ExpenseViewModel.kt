package com.example.bestsplit.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bestsplit.data.database.AppDatabase
import com.example.bestsplit.data.entity.Expense
import com.example.bestsplit.data.repository.ExpenseRepository
import com.example.bestsplit.data.repository.SettlementRepository
import com.example.bestsplit.data.repository.UserRepository
import com.example.bestsplit.data.repository.GroupRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ExpenseRepository
    private val userRepository: UserRepository
    private val groupRepository: GroupRepository
    private val settlementRepository: SettlementRepository

    sealed class ExpenseCreationState {
        object Idle : ExpenseCreationState()
        object Loading : ExpenseCreationState()
        data class Success(val expenseId: Long) : ExpenseCreationState()
        data class Error(val message: String) : ExpenseCreationState()
    }

    sealed class ExpenseUpdateState {
        object Idle : ExpenseUpdateState()
        object Loading : ExpenseUpdateState()
        object Success : ExpenseUpdateState()
        data class Error(val message: String) : ExpenseUpdateState()
    }

    sealed class ExpenseDeletionState {
        object Idle : ExpenseDeletionState()
        object Loading : ExpenseDeletionState()
        object Success : ExpenseDeletionState()
        data class Error(val message: String) : ExpenseDeletionState()
    }

    private val _expenseCreationState =
        MutableStateFlow<ExpenseCreationState>(ExpenseCreationState.Idle)
    val expenseCreationState: StateFlow<ExpenseCreationState> = _expenseCreationState.asStateFlow()

    private val _expenseUpdateState =
        MutableStateFlow<ExpenseUpdateState>(ExpenseUpdateState.Idle)
    val expenseUpdateState: StateFlow<ExpenseUpdateState> = _expenseUpdateState.asStateFlow()

    private val _expenseDeletionState =
        MutableStateFlow<ExpenseDeletionState>(ExpenseDeletionState.Idle)
    val expenseDeletionState: StateFlow<ExpenseDeletionState> = _expenseDeletionState.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        val expenseDao = database.expenseDao()
        repository = ExpenseRepository(expenseDao)
        userRepository = UserRepository()
        groupRepository = GroupRepository(database.groupDao())
        settlementRepository = SettlementRepository(database.settlementDao())
    }

    // Public method to initialize the repository
    fun initializeRepository() {
        repository.initialize()
    }

    // Public method to sync all expenses
    fun syncAllExpensesAsync() {
        viewModelScope.launch {
            // Wait a moment to ensure auth is ready
            delay(500)
            performInitialSync()
        }
    }

    private suspend fun performInitialSync() {
        // Perform initial sync of expenses for all user groups
        val currentUserId = userRepository.getCurrentUserId()
        if (currentUserId.isEmpty()) return

        // Get all user's groups
        val groups = groupRepository.getAllGroupsSync()
        if (groups.isEmpty()) {
            return
        }

        val userGroupIds = groups
            .filter { it.members.contains(currentUserId) }
            .map { it.id }

        // Sync expenses for these groups
        repository.syncAllExpenses(userGroupIds)

        // Also trigger an additional full reload in the background
        fullReloadExpenses()
    }

    // Full reload of expenses for reinstalls
    suspend fun fullReloadExpenses() {
        val currentUserId = userRepository.getCurrentUserId()
        if (currentUserId.isEmpty()) return

        // Get all user's groups
        val groups = groupRepository.getAllGroupsSync()
        if (groups.isEmpty()) {
            return

        }

        val userGroupIds = groups
            .filter { it.members.contains(currentUserId) }
            .map { it.id }

        // Perform the full reload
        repository.fullReloadExpenses(userGroupIds)
    }

    // Sync all expenses for groups the user is a member of
    fun syncAllExpenses() {
        viewModelScope.launch {
            val currentUserId = userRepository.getCurrentUserId()
            if (currentUserId.isEmpty()) return@launch

            // Get all user's groups
            val groups = groupRepository.getAllGroupsSync()
            val userGroupIds = groups
                .filter { it.members.contains(currentUserId) }
                .map { it.id }

            // Sync expenses for these groups
            repository.syncAllExpenses(userGroupIds)
        }
    }

    fun getExpensesForGroup(groupId: Long): Flow<List<Expense>> {
        return repository.getExpensesForGroup(groupId)
    }

    // Force sync for a specific group
    fun syncExpensesForGroup(groupId: Long) {
        viewModelScope.launch {
            try {
                // First perform normal sync
                repository.syncExpensesForGroup(groupId)

                // Additional sync to ensure all data is retrieved
                delay(300)
                repository.syncExpensesForGroup(groupId)
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Error syncing expenses for group $groupId", e)
            }
        }
    }

    fun addExpense(
        groupId: Long,
        description: String,
        amount: Double,
        paidBy: String,
        paidFor: Map<String, Double>
    ) {
        viewModelScope.launch {
            try {
                _expenseCreationState.value = ExpenseCreationState.Loading

                val expense = Expense(
                    groupId = groupId,
                    description = description,
                    amount = amount,
                    paidBy = paidBy,
                    paidFor = paidFor,
                    createdAt = System.currentTimeMillis()
                )

                val id = repository.addExpense(expense)
                _expenseCreationState.value = ExpenseCreationState.Success(id)
            } catch (e: Exception) {
                _expenseCreationState.value =
                    ExpenseCreationState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetExpenseCreationState() {
        _expenseCreationState.value = ExpenseCreationState.Idle
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                _expenseUpdateState.value = ExpenseUpdateState.Loading

                val success = repository.updateExpense(expense)

                if (success) {
                    _expenseUpdateState.value = ExpenseUpdateState.Success
                } else {
                    _expenseUpdateState.value = ExpenseUpdateState.Error("Failed to update expense")
                }
            } catch (e: Exception) {
                _expenseUpdateState.value = ExpenseUpdateState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteExpense(expenseId: Long, groupId: Long) {
        viewModelScope.launch {
            try {
                _expenseDeletionState.value = ExpenseDeletionState.Loading

                val success = repository.deleteExpense(expenseId, groupId)

                if (success) {
                    _expenseDeletionState.value = ExpenseDeletionState.Success
                } else {
                    _expenseDeletionState.value =
                        ExpenseDeletionState.Error("Failed to delete expense")
                }
            } catch (e: Exception) {
                _expenseDeletionState.value =
                    ExpenseDeletionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetExpenseUpdateState() {
        _expenseUpdateState.value = ExpenseUpdateState.Idle
    }

    fun resetExpenseDeletionState() {
        _expenseDeletionState.value = ExpenseDeletionState.Idle
    }

    // Calculate balances between members in a group based on expenses and settlements
    suspend fun calculateBalances(
        groupId: Long,
        members: List<String>
    ): Map<String, Map<String, Double>> {
        try {
            // Map of user to map of other users to amount owed
            val balances = mutableMapOf<String, MutableMap<String, Double>>()

            // Initialize balances for each member
            members.forEach { member ->
                balances[member] = mutableMapOf()
                members.forEach { otherMember ->
                    if (member != otherMember) {
                        balances[member]!![otherMember] = 0.0
                    }
                }
            }

            // Get expenses for group
            val expenses = try {
                repository.getExpensesForGroupAsList(groupId)
            } catch (e: Exception) {
                // Return empty balances if we can't get expenses
                return balances
            }

            // Process each expense
            expenses.forEach { expense ->
                val paidBy = expense.paidBy
                val paidFor = expense.paidFor

                // Skip expenses with invalid data
                if (paidBy.isBlank() || !members.contains(paidBy) || paidFor.isEmpty()) {
                    return@forEach
                }

                // Process each member who the expense was paid for
                paidFor.entries.forEach { entry ->
                    val memberId = entry.key
                    val amount = entry.value

                    // Skip if member not in the group or amount is invalid
                    if (memberId.isBlank() || !members.contains(memberId) || amount <= 0) {
                        return@forEach
                    }

                    // Skip self-payments
                    if (memberId != paidBy) {
                        // Create maps if they don't exist (defensive coding)
                        if (!balances.containsKey(memberId)) balances[memberId] = mutableMapOf()
                        if (!balances.containsKey(paidBy)) balances[paidBy] = mutableMapOf()

                        if (!balances[memberId]!!.containsKey(paidBy)) balances[memberId]!![paidBy] =
                            0.0
                        if (!balances[paidBy]!!.containsKey(memberId)) balances[paidBy]!![memberId] =
                            0.0

                        // Update how much this member owes the payer
                        balances[memberId]!![paidBy] =
                            (balances[memberId]!![paidBy] ?: 0.0) + amount
                        // Update how much the payer is owed by this member
                        balances[paidBy]!![memberId] =
                            (balances[paidBy]!![memberId] ?: 0.0) - amount
                    }
                }
            }

            // Apply settlements to balances
            try {
                val settlements = settlementRepository.getSettlementsForGroupAsList(groupId)

                // Process each settlement
                settlements.forEach { settlement ->
                    val fromUser = settlement.fromUserId
                    val toUser = settlement.toUserId
                    val amount = settlement.amount

                    // Skip settlements with invalid data
                    if (fromUser.isBlank() || toUser.isBlank() ||
                        !members.contains(fromUser) || !members.contains(toUser) ||
                        amount <= 0
                    ) {
                        return@forEach
                    }

                    // Create maps if they don't exist
                    if (!balances.containsKey(fromUser)) balances[fromUser] = mutableMapOf()
                    if (!balances.containsKey(toUser)) balances[toUser] = mutableMapOf()

                    if (!balances[fromUser]!!.containsKey(toUser)) balances[fromUser]!![toUser] =
                        0.0
                    if (!balances[toUser]!!.containsKey(fromUser)) balances[toUser]!![fromUser] =
                        0.0

                    // Update balances based on settlement
                    // fromUser paid toUser, so reduce what fromUser owes toUser
                    balances[fromUser]!![toUser] = (balances[fromUser]!![toUser] ?: 0.0) - amount
                    // increase what toUser owes fromUser
                    balances[toUser]!![fromUser] = (balances[toUser]!![fromUser] ?: 0.0) + amount
                }
            } catch (e: Exception) {
                // Log the error and continue with expense-only balances
            }

            // Simplify balances (netting off mutual debts)
            members.forEach { member ->
                // Skip if member not in balances
                if (!balances.containsKey(member)) return@forEach

                members.forEach { otherMember ->
                    // Skip if other member not in balances or if it's the same member
                    if (member == otherMember || !balances.containsKey(otherMember)) return@forEach

                    // Ensure the maps contain entries for each other
                    if (!balances[member]!!.containsKey(otherMember)) balances[member]!![otherMember] =
                        0.0
                    if (!balances[otherMember]!!.containsKey(member)) balances[otherMember]!![member] =
                        0.0

                    val amountOwed = balances[member]!![otherMember] ?: 0.0
                    val amountOwedBack = balances[otherMember]!![member] ?: 0.0

                    if (amountOwed > 0 && amountOwedBack > 0) {
                        if (amountOwed > amountOwedBack) {
                            balances[member]!![otherMember] = amountOwed - amountOwedBack
                            balances[otherMember]!![member] = 0.0
                        } else {
                            balances[otherMember]!![member] = amountOwedBack - amountOwed
                            balances[member]!![otherMember] = 0.0
                        }
                    }
                }
            }

            return balances
        } catch (e: Exception) {
            // If any unexpected error occurs, return empty balances
            return emptyMap()
        }
    }

    // Get user details for display
    suspend fun getUserDetails(userId: String): UserRepository.User? {
        return userRepository.getUserById(userId)
    }

    suspend fun getExpenseById(expenseId: Long): Expense? {
        return repository.getExpenseById(expenseId)
    }
}