package com.example.bestsplit.data.transfer

import android.content.Context
import android.content.Intent
import com.example.bestsplit.data.entity.Group
import com.example.bestsplit.data.repository.GroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataTransferManager(
    private val context: Context,
    private val groupRepository: GroupRepository
) {
    suspend fun shareGroup(groupId: Long) {
        val group = groupRepository.getGroupById(groupId) ?: return
        val jsonData = group.toJson()

        withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Group: ${group.name}")
                putExtra(Intent.EXTRA_TEXT, jsonData)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Group"))
        }
    }

    suspend fun importGroup(jsonData: String): Long? {
        val group = Group.fromJson(jsonData) ?: return null
        // Create a new instance with id=0 to avoid conflicts
        val newGroup = group.copy(id = 0)
        return groupRepository.insertGroup(newGroup)
    }
}