package com.example.bestsplit.data.repository

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.example.bestsplit.data.dao.GroupDao
import com.example.bestsplit.data.entity.Group
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.sqlite.db.SimpleSQLiteQuery

class GroupRepository(
    private val groupDao: GroupDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val TAG = "GroupRepository"
    private val COLLECTION_GROUPS = "groups"
    private val COLLECTION_USER_GROUPS = "groups"
    private val applicationScope = CoroutineScope(Dispatchers.IO)

    val allGroups: Flow<List<Group>> = groupDao.getAllGroups()

    // Flag to track if initial sync has been performed
    private var initialSyncPerformed = false

    // Public method to perform initial sync with more aggressiveness
    fun performInitialSync() {
        if (initialSyncPerformed) return

        val userId = auth.currentUser?.uid
        if (userId.isNullOrEmpty()) {
            Log.e(TAG, "Cannot initialize group repository - no authenticated user")
            return
        }

        Log.d(TAG, "Performing initial group sync for user: $userId")

        applicationScope.launch {
            try {
                // Force a full sync from cloud
                val success = syncFromCloud()

                if (success) {
                    Log.d(TAG, "Initial group sync completed successfully")
                    initialSyncPerformed = true
                } else {
                    Log.e(TAG, "Initial group sync failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during initial group sync", e)
            }
        }
    }

    private fun registerActivityLifecycleCallbacks() {
        val application = Application.getProcessName()?.let {
            try {
                Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as? Application
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get application instance", e)
                null
            }
        }

        application?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                // When any activity resumes, sync groups
                applicationScope.launch {
                    Log.d(TAG, "Activity resumed, syncing groups")
                    syncFromCloud()
                }
            }

            // Implement remaining callbacks
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    suspend fun insertGroup(group: Group, memberIds: List<String> = emptyList()): Long {
        val currentUserId = auth.currentUser?.uid ?: return -1

        // Include creator as a member and add other selected members
        val allMembers = (listOf(currentUserId) + memberIds).distinct()


        val groupWithMembers = group.copy(
            createdBy = currentUserId,
            members = allMembers
        )

        // Save to local database
        val id = groupDao.insertGroup(groupWithMembers)
        val finalGroup = groupWithMembers.copy(id = id)

        try {
            // Create group in Firestore
            firestore.collection(COLLECTION_GROUPS)
                .document(id.toString())
                .set(finalGroup)
                .await()

            // Add reference to this group for each member
            for (memberId in allMembers) {
                firestore.collection("users")
                    .document(memberId)
                    .collection(COLLECTION_USER_GROUPS)
                    .document(id.toString())
                    .set(mapOf("groupId" to id))
                    .await()
            }

            Log.d(TAG, "Group created with ${allMembers.size} members")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating group in Firestore", e)
        }

        return id
    }

    suspend fun updateGroup(group: Group) {
        groupDao.updateGroup(group)

        try {
            firestore.collection(COLLECTION_GROUPS)
                .document(group.id.toString())
                .set(group)
                .await()

            // Update member references if needed
            handleMembershipChanges(group)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating group in Firestore", e)
        }
    }

    private suspend fun handleMembershipChanges(group: Group) {
        try {
            // Get current member list from Firestore
            val document = firestore.collection(COLLECTION_GROUPS)
                .document(group.id.toString())
                .get()
                .await()

            val existingGroup = document.toObject<Group>()
            val oldMembers = existingGroup?.members ?: emptyList()
            val newMembers = group.members

            // Add references for new members
            val addedMembers = newMembers.filter { it !in oldMembers }
            for (memberId in addedMembers) {
                firestore.collection("users")
                    .document(memberId)
                    .collection(COLLECTION_USER_GROUPS)
                    .document(group.id.toString())
                    .set(mapOf("groupId" to group.id))
                    .await()
            }

            // Remove references for removed members
            val removedMembers = oldMembers.filter { it !in newMembers }
            for (memberId in removedMembers) {
                firestore.collection("users")
                    .document(memberId)
                    .collection(COLLECTION_USER_GROUPS)
                    .document(group.id.toString())
                    .delete()
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating group membership", e)
        }
    }

    suspend fun deleteGroup(group: Group) {
        groupDao.deleteGroup(group)

        try {
            // Remove group document
            firestore.collection(COLLECTION_GROUPS)
                .document(group.id.toString())
                .delete()
                .await()

            // Remove all member references
            for (memberId in group.members) {
                firestore.collection("users")
                    .document(memberId)
                    .collection(COLLECTION_USER_GROUPS)
                    .document(group.id.toString())
                    .delete()
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting group from Firestore", e)
        }
    }

    suspend fun getGroupById(id: Long): Group? {
        return groupDao.getGroupById(id)
    }

    fun getGroupsWithMember(userId: String): Flow<List<Group>> {
        val query = SimpleSQLiteQuery(
            "SELECT * FROM `groups` WHERE members LIKE ?",
            arrayOf("%\"$userId\"%")
        )
        return groupDao.getGroupsWithMemberRaw(query)
    }

    suspend fun getAllGroupsSync(): List<Group> {
        return groupDao.getAllGroupsSync()
    }

    suspend fun syncFromCloud(): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        Log.d(TAG, "Starting group sync for user: $currentUserId")

        try {
            // Get all locally stored groups
            val localGroups = groupDao.getAllGroupsSync()
            val localGroupIds = localGroups.map { it.id }.toSet()

            // Query cloud for groups where current user is a member
            val groupsQuerySnapshot = firestore.collection(COLLECTION_GROUPS)
                .whereArrayContains("members", currentUserId)
                .get()
                .await()

            Log.d(TAG, "Found ${groupsQuerySnapshot.size()} groups in cloud for user")

            // Track which cloud groups we've processed
            val processedCloudGroupIds = mutableSetOf<Long>()

            // Process cloud groups - update existing or insert new
            for (document in groupsQuerySnapshot.documents) {
                val cloudGroup = document.toObject<Group>()
                if (cloudGroup != null) {
                    processedCloudGroupIds.add(cloudGroup.id)

                    // Update or insert group
                    if (cloudGroup.id in localGroupIds) {
                        Log.d(TAG, "Updating existing group: ${cloudGroup.name}")
                        groupDao.updateGroup(cloudGroup)
                    } else {
                        Log.d(TAG, "Inserting new group: ${cloudGroup.name}")
                        groupDao.insertGroup(cloudGroup)
                    }
                } else {
                    Log.e(TAG, "Unable to parse group document: ${document.id}")
                }
            }

            // Handle local groups not found in cloud
            val orphanedGroupIds = localGroupIds - processedCloudGroupIds
            for (groupId in orphanedGroupIds) {
                val orphanedGroup = localGroups.find { it.id == groupId } ?: continue

                // Verify if group exists in cloud but user was removed
                val groupDoc = firestore.collection(COLLECTION_GROUPS)
                    .document(groupId.toString())
                    .get()
                    .await()

                if (!groupDoc.exists()) {
                    // Group was deleted from Firestore
                    Log.d(TAG, "Deleting locally orphaned group: ${orphanedGroup.name}")
                    groupDao.deleteGroup(orphanedGroup)
                } else {
                    // Group exists but user might be removed from members
                    val cloudGroup = groupDoc.toObject<Group>()
                    if (cloudGroup != null && currentUserId !in cloudGroup.members) {
                        Log.d(TAG, "User removed from group ${orphanedGroup.name}, deleting locally")
                        groupDao.deleteGroup(orphanedGroup)
                    }
                }
            }

            Log.d(TAG, "Group sync completed successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing groups from Firestore", e)
            return false
        }
    }

    suspend fun addMemberToGroup(groupId: Long, userId: String) {
        val group = getGroupById(groupId) ?: return
        if (userId in group.members) return

        val updatedMembers = group.members + userId
        val updatedGroup = group.copy(members = updatedMembers)

        updateGroup(updatedGroup)
    }

    suspend fun removeMemberFromGroup(groupId: Long, userId: String) {
        val group = getGroupById(groupId) ?: return
        if (userId !in group.members) return

        val updatedMembers = group.members.filter { it != userId }
        val updatedGroup = group.copy(members = updatedMembers)

        updateGroup(updatedGroup)
    }
}