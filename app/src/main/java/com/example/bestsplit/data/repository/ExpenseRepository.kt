package com.example.bestsplit.data.repository

import android.util.Log
import com.example.bestsplit.data.dao.ExpenseDao
import com.example.bestsplit.data.entity.Expense
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val TAG = "ExpenseRepository"
    private val COLLECTION_EXPENSES = "expenses"

    fun getExpensesForGroup(groupId: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesForGroup(groupId)
    }

    suspend fun addExpense(expense: Expense): Long {
        val id = expenseDao.insertExpense(expense)

        // Save to Firebase with ID
        try {
            val expenseWithId = expense.copy(id = id)
            firestore.collection(COLLECTION_EXPENSES)
                .document(id.toString())
                .set(expenseWithId)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving expense to Firestore", e)
        }

        return id
    }

    // Add other CRUD operations
}