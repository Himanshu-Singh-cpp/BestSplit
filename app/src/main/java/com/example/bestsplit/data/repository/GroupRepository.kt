// app/src/main/java/com/example/bestsplit/data/repository/GroupRepository.kt
package com.example.bestsplit.data.repository

import com.example.bestsplit.data.dao.GroupDao
import com.example.bestsplit.data.entity.Group
import kotlinx.coroutines.flow.Flow

class GroupRepository(private val groupDao: GroupDao) {
    val allGroups: Flow<List<Group>> = groupDao.getAllGroups()

    suspend fun insertGroup(group: Group): Long {
        return groupDao.insertGroup(group)
    }

    suspend fun updateGroup(group: Group) {
        groupDao.updateGroup(group)
    }

    suspend fun deleteGroup(group: Group) {
        groupDao.deleteGroup(group)
    }

    suspend fun getGroupById(id: Long): Group? {
        return groupDao.getGroupById(id)
    }
}