// app/src/main/java/com/example/bestsplit/data/dao/GroupDao.kt
package com.example.bestsplit.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.bestsplit.data.entity.Group
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Insert
    suspend fun insertGroup(group: Group): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupWithId(group: Group): Long

    @Update
    suspend fun updateGroup(group: Group)

    @Delete
    suspend fun deleteGroup(group: Group)

    @Query("SELECT * FROM `groups`")
    suspend fun getAllGroupsSync(): List<Group>

    @Query("SELECT * FROM `groups` ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<Group>>

    @Query("SELECT * FROM `groups` WHERE id = :groupId")
    suspend fun getGroupById(groupId: Long): Group?

    @RawQuery(observedEntities = [Group::class])
    fun getGroupsWithMemberRaw(query: SupportSQLiteQuery): Flow<List<Group>>
}