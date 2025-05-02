package com.example.bestsplit.data.repository

import android.util.Log
import com.example.bestsplit.data.dao.ExpenseDao
import com.example.bestsplit.data.entity.Expense
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val TAG = "ExpenseRepository"
    private val COLLECTION_GROUPS = "groups" // Root collection for groups
    private val SUBCOLLECTION_EXPENSES = "expenses" // Subcollection for expenses within each group
    private val OLD_COLLECTION_EXPENSES = "expenses" // Old top-level expenses collection
    private val applicationScope = CoroutineScope(Dispatchers.IO)

    // Store listener registrations to clean up later
    private val listeners = mutableMapOf<Long, ListenerRegistration>()

    // Track if we've already synced, to avoid duplicate syncs
    private var initialSyncPerformed = false

    init {
        // Configure Firestore for better real-time sync
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        firestore.firestoreSettings = settings

        // We'll call migrations and syncs explicitly when a user is authenticated
    }

    // Should be called after authentication is confirmed
    fun initialize() {
        if (initialSyncPerformed) return

        // Check if authentication is available
        val userId = auth.currentUser?.uid
        if (userId.isNullOrEmpty()) {
            Log.e(TAG, "Cannot initialize expense repository - no authenticated user")
            return
        }

        Log.d(TAG, "Initializing expense repository for user: $userId")
        applicationScope.launch {
            try {
                // Perform migration first
                migrateExpensesToSubcollections()

                initialSyncPerformed = true
            } catch (e: Exception) {
                Log.e(TAG, "Error during expense repository initialization", e)
            }
        }
    }

    // Migration function to move expenses to subcollections
    private suspend fun migrateExpensesToSubcollections() {
        try {
            Log.d(TAG, "Starting expense migration check...")

            // Check if old collection exists
            val oldExpenses = firestore.collection(OLD_COLLECTION_EXPENSES).get().await()

            if (oldExpenses.isEmpty) {
                Log.d(TAG, "No old expenses to migrate")
                return
            }

            Log.d(TAG, "Found ${oldExpenses.size()} expenses to migrate to subcollections")

            // Process each old expense
            for (document in oldExpenses.documents) {
                try {
                    val expense = document.toObject(Expense::class.java) ?: continue

                    // Skip invalid expenses
                    if (expense.id <= 0 || expense.groupId <= 0) {
                        Log.d(TAG, "Skipping invalid expense: ${document.id}")
                        continue
                    }

                    // Create an explicit map for Firestore to avoid serialization issues
                    val expenseMap = mapOf(
                        "id" to expense.id,
                        "groupId" to expense.groupId,
                        "description" to expense.description,
                        "amount" to expense.amount,
                        "paidBy" to expense.paidBy,
                        "paidFor" to expense.paidFor,
                        "createdAt" to expense.createdAt
                    )

                    // Store in new location using the map
                    firestore.collection(COLLECTION_GROUPS)
                        .document(expense.groupId.toString())
                        .collection(SUBCOLLECTION_EXPENSES)
                        .document(expense.id.toString())
                        .set(expenseMap)
                        .await()

                    Log.d(
                        TAG,
                        "Migrated expense ${expense.id} to group ${expense.groupId} subcollection"
                    )

                    // Also save to local database
                    try {
                        expenseDao.insertExpense(expense)
                        Log.d(TAG, "Saved old-style expense ${expense.id}")
                    } catch (sqlEx: Exception) {
                        // Handle foreign key constraint errors
                        if (sqlEx.message?.contains("FOREIGN KEY constraint failed") == true) {
                            Log.w(
                                TAG,
                                "Foreign key constraint failed for old expense ${expense.id}"
                            )
                        } else {
                            Log.e(TAG, "Error inserting old expense: ${sqlEx.message}")
                        }
                    }

                    // Delete from old location (optional - can be commented out for safety)
                    // document.reference.delete().await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error migrating expense", e)
                }
            }

            Log.d(TAG, "Expense migration completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate expenses", e)
        }
    }

    fun getExpensesForGroup(groupId: Long): Flow<List<Expense>> {
        // Start listening to real-time updates for this group
        setupRealtimeSync(groupId)
        return expenseDao.getExpensesForGroup(groupId)
    }

    suspend fun getExpensesForGroupAsList(groupId: Long): List<Expense> {
        // Make sure we have the latest data
        syncExpensesForGroup(groupId)
        return expenseDao.getExpensesForGroupSync(groupId)
    }

    suspend fun getExpenseById(expenseId: Long): Expense? {
        return expenseDao.getExpenseById(expenseId)
    }

    suspend fun addExpense(expense: Expense): Long {
        // First add to local database
        val id = expenseDao.insertExpense(expense)
        val expenseWithId = expense.copy(id = id)

        // Log the expense being saved
        Log.d(
            TAG,
            "Adding expense to Firestore: ID=$id, Group=${expense.groupId}, Amount=${expense.amount}, Description='${expense.description}'"
        )
        Log.d(
            TAG,
            "PaidFor data: ${expense.paidFor.entries.joinToString { "${it.key}=${it.value}" }}"
        )

        // Then push to Firebase with ID - use the correct path for expenses
        try {
            // Use an explicit map to ensure data is properly structured for Firestore
            val expenseData = mapOf(
                "id" to id,
                "groupId" to expense.groupId,
                "description" to expense.description,
                "amount" to expense.amount,
                "paidBy" to expense.paidBy,
                "paidFor" to expense.paidFor,
                "createdAt" to expense.createdAt
            )

            // Store expenses as a subcollection of groups
            firestore.collection(COLLECTION_GROUPS)
                .document(expense.groupId.toString())
                .collection(SUBCOLLECTION_EXPENSES)
                .document(id.toString())
                .set(expenseData)
                .await()

            Log.d(TAG, "Expense saved to Firestore with ID: $id in group ${expense.groupId}")

            // Also save to old collection for backward compatibility
            firestore.collection(OLD_COLLECTION_EXPENSES)
                .document(id.toString())
                .set(expenseData)
                .await()

            Log.d(TAG, "Expense also saved to old collection for compatibility")

            // Force a sync after adding a new expense
            syncExpensesForGroup(expense.groupId)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving expense to Firestore", e)

            // If Firebase push fails, retry with a delay
            applicationScope.launch {
                delay(1000)
                try {
                    // Retry with the same explicit data structure
                    val retryExpenseData = mapOf(
                        "id" to id,
                        "groupId" to expense.groupId,
                        "description" to expense.description,
                        "amount" to expense.amount,
                        "paidBy" to expense.paidBy,
                        "paidFor" to expense.paidFor,
                        "createdAt" to expense.createdAt
                    )

                    // First to the group subcollection
                    firestore.collection(COLLECTION_GROUPS)
                        .document(expense.groupId.toString())
                        .collection(SUBCOLLECTION_EXPENSES)
                        .document(id.toString())
                        .set(retryExpenseData)
                        .await()

                    // Then to the old collection
                    firestore.collection(OLD_COLLECTION_EXPENSES)
                        .document(id.toString())
                        .set(retryExpenseData)
                        .await()

                    Log.d(TAG, "Expense retry save successful with ID: $id")
                } catch (e: Exception) {
                    Log.e(TAG, "Retry save failed for expense ID: $id", e)
                }
            }
        }

        return id
    }

    suspend fun updateExpense(expense: Expense): Boolean {
        try {
            // First update local database
            expenseDao.insertExpense(expense)

            Log.d(
                TAG,
                "Updating expense: ID=${expense.id}, Group=${expense.groupId}, Amount=${expense.amount}"
            )

            // Create explicit map for Firestore
            val expenseData = mapOf(
                "id" to expense.id,
                "groupId" to expense.groupId,
                "description" to expense.description,
                "amount" to expense.amount,
                "paidBy" to expense.paidBy,
                "paidFor" to expense.paidFor,
                "createdAt" to expense.createdAt
            )

            // Update in group subcollection
            firestore.collection(COLLECTION_GROUPS)
                .document(expense.groupId.toString())
                .collection(SUBCOLLECTION_EXPENSES)
                .document(expense.id.toString())
                .set(expenseData, SetOptions.merge())
                .await()

            // Also update in old collection for compatibility
            firestore.collection(OLD_COLLECTION_EXPENSES)
                .document(expense.id.toString())
                .set(expenseData, SetOptions.merge())
                .await()

            Log.d(TAG, "Successfully updated expense ${expense.id} in Firestore")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating expense ${expense.id}", e)
            return false
        }
    }

    suspend fun deleteExpense(expenseId: Long, groupId: Long): Boolean {
        try {
            // Create a minimal expense object with the ID for deletion
            val expense = Expense(
                id = expenseId,
                groupId = groupId,
                description = "",
                amount = 0.0,
                paidBy = "",
                paidFor = emptyMap(),
                createdAt = 0L
            )

            // First delete from local database
            expenseDao.deleteExpense(expense)

            Log.d(TAG, "Deleting expense: ID=$expenseId from group $groupId")

            // Delete from group subcollection
            firestore.collection(COLLECTION_GROUPS)
                .document(groupId.toString())
                .collection(SUBCOLLECTION_EXPENSES)
                .document(expenseId.toString())
                .delete()
                .await()

            // Also delete from old collection
            firestore.collection(OLD_COLLECTION_EXPENSES)
                .document(expenseId.toString())
                .delete()
                .await()

            Log.d(TAG, "Successfully deleted expense $expenseId from Firestore")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting expense $expenseId", e)
            return false
        }
    }

    // Setup real-time sync for a specific group
    private fun setupRealtimeSync(groupId: Long) {
        // Avoid registering multiple listeners for the same group
        if (listeners.containsKey(groupId)) {
            // Refresh the listener if it already exists
            removeListener(groupId)
        }

        // Listen to expenses as a subcollection of the group
        val listener = firestore.collection(COLLECTION_GROUPS)
            .document(groupId.toString())
            .collection(SUBCOLLECTION_EXPENSES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for expense updates", error)
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    applicationScope.launch {
                        try {
                            val expenses = snapshots.toObjects<Expense>()
                            Log.d(
                                TAG,
                                "Received ${expenses.size} expense updates for group $groupId"
                            )

                            // Skip update if no expenses or invalid data
                            if (expenses.isEmpty()) return@launch

                            // Track if we actually inserted any new data
                            var dataChanged = false

                            // Update local database one by one to avoid transaction issues
                            withContext(Dispatchers.IO) {
                                expenses.forEach { expense ->
                                    try {
                                        if (expense.id > 0 && expense.groupId == groupId) {
                                            // Check if expense exists first
                                            val existing = expenseDao.getExpenseById(expense.id)
                                            if (existing == null) {
                                                // This is a new expense
                                                dataChanged = true
                                                Log.d(TAG, "New expense detected: ${expense.id}")
                                            }

                                            try {
                                                expenseDao.insertExpense(expense)
                                            } catch (sqlEx: Exception) {
                                                // Check if it's a foreign key constraint error
                                                if (sqlEx.message?.contains("FOREIGN KEY constraint failed") == true) {
                                                    Log.w(
                                                        TAG,
                                                        "Foreign key constraint failed for expense ${expense.id}. Group ${expense.groupId} might not exist in local database yet."
                                                    )
                                                } else {
                                                    Log.e(
                                                        TAG,
                                                        "Error inserting expense: ${sqlEx.message}"
                                                    )
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                            TAG,
                                            "Error updating expense in Room: ${expense.id}",
                                            e
                                        )
                                    }
                                }
                            }

                            if (dataChanged) {
                                Log.d(TAG, "New expenses added, triggering UI refresh")
                                // You could trigger a UI refresh here if needed
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing expense snapshot", e)
                        }
                    }
                }
            }

        listeners[groupId] = listener
        Log.d(TAG, "Real-time sync established for group $groupId")
    }

    // Sync expenses on-demand for a group
    suspend fun syncExpensesForGroup(groupId: Long) {
        try {
            Log.d(TAG, "Manually syncing expenses for group $groupId")

            // Get all expenses for this group from Firebase subcollection
            val snapshot = firestore.collection(COLLECTION_GROUPS)
                .document(groupId.toString())
                .collection(SUBCOLLECTION_EXPENSES)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val expenses = snapshot.toObjects(Expense::class.java)

            Log.d(TAG, "Retrieved ${expenses.size} expenses from subcollection for group $groupId")

            // Process expenses from subcollection
            if (expenses.isNotEmpty()) {
                processRetrievedExpenses(expenses, groupId)
            }

            // Always check the old collection too for completeness
            val oldSnapshot = firestore.collection(OLD_COLLECTION_EXPENSES)
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            if (!oldSnapshot.isEmpty) {
                Log.d(
                    TAG,
                    "Found ${oldSnapshot.size()} expenses in old collection for group $groupId"
                )
                val oldExpenses =
                    oldSnapshot.documents.mapNotNull { it.toObject(Expense::class.java) }

                // Process these expenses too
                processRetrievedExpenses(oldExpenses, groupId)

                // If we found expenses in the old collection but not in the subcollection,
                // migrate them to the subcollection
                if (expenses.isEmpty() && oldExpenses.isNotEmpty()) {
                    Log.d(TAG, "Migrating ${oldExpenses.size} expenses to subcollection")
                    oldExpenses.forEach { expense ->
                        try {
                            // Create an explicit map for Firestore to avoid serialization issues
                            val expenseMap = mapOf(
                                "id" to expense.id,
                                "groupId" to expense.groupId,
                                "description" to expense.description,
                                "amount" to expense.amount,
                                "paidBy" to expense.paidBy,
                                "paidFor" to expense.paidFor,
                                "createdAt" to expense.createdAt
                            )

                            // Store in new location using the map
                            firestore.collection(COLLECTION_GROUPS)
                                .document(expense.groupId.toString())
                                .collection(SUBCOLLECTION_EXPENSES)
                                .document(expense.id.toString())
                                .set(expenseMap)
                                .await()

                            Log.d(TAG, "Migrated expense ${expense.id} to subcollection")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error migrating expense ${expense.id}", e)
                        }
                    }
                }
            } else if (expenses.isEmpty()) {
                Log.d(TAG, "No expenses found for group $groupId in either location")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing expenses from Firestore", e)
        }
    }

    // Helper method to process retrieved expenses and save to local database
    private suspend fun processRetrievedExpenses(expenses: List<Expense>, groupId: Long) {
        // Update local database
        withContext(Dispatchers.IO) {
            for (expense in expenses) {
                try {
                    // Skip invalid expenses
                    if (expense.id <= 0) {
                        Log.w(TAG, "Skipping expense with invalid ID")
                        continue
                    }

                    if (expense.groupId != groupId) {
                        Log.w(
                            TAG,
                            "Expense ${expense.id} has mismatched group ID: ${expense.groupId} vs $groupId"
                        )
                        continue
                    }

                    Log.d(
                        TAG,
                        "Trying to save expense ${expense.id} to local database. Amount: ${expense.amount}"
                    )

                    try {
                        expenseDao.insertExpense(expense)
                        Log.d(TAG, "Successfully saved expense ${expense.id}")
                    } catch (sqlEx: Exception) {
                        // Check if it's a foreign key constraint error
                        if (sqlEx.message?.contains("FOREIGN KEY constraint failed") == true) {
                            Log.w(
                                TAG,
                                "Foreign key constraint failed for expense ${expense.id}. Group ${expense.groupId} might not exist in local database yet."
                            )
                        } else {
                            // Re-throw any other exception
                            throw sqlEx
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing expense ${expense.id}: ${e.message}")
                }
            }
        }
    }

    // Sync all expenses for groups the current user is a member of
    suspend fun syncAllExpenses(userGroupIds: List<Long>) {
        try {
            val currentUserId = auth.currentUser?.uid ?: return
            Log.d(
                TAG,
                "Syncing all expenses for user $currentUserId in ${userGroupIds.size} groups"
            )

            // For each group the user is a member of
            for (groupId in userGroupIds) {
                syncExpensesForGroup(groupId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing all expenses", e)
        }
    }

    // Perform a complete sync of all expenses, including checking both old and new structures
    // This is especially useful for reinstalls
    suspend fun fullReloadExpenses(userGroupIds: List<Long>) {
        try {
            val currentUserId = auth.currentUser?.uid ?: return
            Log.d(TAG, "Performing FULL expense reload for user $currentUserId")

            // First migrate any old expenses
            migrateExpensesToSubcollections()

            // Then sync from new structure
            for (groupId in userGroupIds) {
                try {
                    Log.d(TAG, "Full reload for group $groupId")

                    // Try to load expenses as subcollection
                    val snapshot = firestore.collection(COLLECTION_GROUPS)
                        .document(groupId.toString())
                        .collection(SUBCOLLECTION_EXPENSES)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .get()
                        .await()

                    val expenses = snapshot.toObjects(Expense::class.java)
                    Log.d(TAG, "Found ${expenses.size} expenses in group $groupId")

                    // Save to database
                    withContext(Dispatchers.IO) {
                        for (expense in expenses) {
                            if (expense.id > 0 && expense.groupId == groupId) {
                                expenseDao.insertExpense(expense)
                                Log.d(
                                    TAG,
                                    "Saved expense ${expense.id} for group $groupId - amount: ${expense.amount}"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in full reload for group $groupId", e)
                }
            }

            // Also check for any expenses in the old collection structure
            try {
                val oldExpenses = firestore.collection(OLD_COLLECTION_EXPENSES)
                    .whereIn("groupId", userGroupIds.map { it.toString() })
                    .get()
                    .await()

                if (!oldExpenses.isEmpty) {
                    Log.d(TAG, "Found ${oldExpenses.size()} expenses in old collection")

                    // Process and save each expense
                    for (document in oldExpenses.documents) {
                        val expense = document.toObject(Expense::class.java)
                        if (expense != null && expense.id > 0 && userGroupIds.contains(expense.groupId)) {
                            // Save to local database
                            expenseDao.insertExpense(expense)
                            Log.d(TAG, "Saved old-style expense ${expense.id}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking old expenses", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in full reload of expenses", e)
        }
    }

    // Clean up listeners when no longer needed
    fun removeListener(groupId: Long) {
        listeners[groupId]?.remove()
        listeners.remove(groupId)
        Log.d(TAG, "Removed expense listener for group $groupId")
    }

    fun removeAllListeners() {
        for ((groupId, listener) in listeners) {
            listener.remove()
            Log.d(TAG, "Removed expense listener for group $groupId")
        }
        listeners.clear()
    }
}