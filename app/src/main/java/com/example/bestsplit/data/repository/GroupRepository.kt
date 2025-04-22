package com.example.bestsplit.data.repository

import android.util.Log
import com.example.bestsplit.data.dao.GroupDao
import com.example.bestsplit.data.entity.Group
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class GroupRepository(
    private val groupDao: GroupDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val TAG = "GroupRepository"
    private val COLLECTION_GROUPS = "groups"

    val allGroups: Flow<List<Group>> = groupDao.getAllGroups()

    suspend fun insertGroup(group: Group): Long {
        val id = groupDao.insertGroup(group)

        // Save to Firebase with same ID
        try {
            val groupWithId = group.copy(id = id)
            firestore.collection(COLLECTION_GROUPS)
                .document(id.toString())
                .set(groupWithId)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving group to Firestore", e)
        }

        return id
    }

    suspend fun updateGroup(group: Group) {
        groupDao.updateGroup(group)

        // Update in Firebase
        try {
            firestore.collection(COLLECTION_GROUPS)
                .document(group.id.toString())
                .set(group)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating group in Firestore", e)
        }
    }

    suspend fun deleteGroup(group: Group) {
        groupDao.deleteGroup(group)

        // Delete from Firebase
        try {
            firestore.collection(COLLECTION_GROUPS)
                .document(group.id.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting group from Firestore", e)
        }
    }

    suspend fun getGroupById(id: Long): Group? {
        return groupDao.getGroupById(id)
    }

    // Sync data from Firebase
    suspend fun syncFromCloud() {
        try {
            val documents = firestore.collection(COLLECTION_GROUPS).get().await()
            for (document in documents) {
                val group = document.toObject<Group>()
                groupDao.insertGroup(group)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing from Firestore", e)
        }
    }

    suspend fun addMemberToGroup(groupId: Long, userId: String) {
        val group = getGroupById(groupId) ?: return
        val updatedMembers = group.members + userId
        val updatedGroup = group.copy(members = updatedMembers)

        // Update locally
        groupDao.updateGroup(updatedGroup)

        // Update in Firebase
        try {
            firestore.collection(COLLECTION_GROUPS)
                .document(groupId.toString())
                .update("members", updatedMembers)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating group members in Firestore", e)
        }
    }
}