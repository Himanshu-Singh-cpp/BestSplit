package com.example.bestsplit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bestsplit.data.entity.Settlement
import kotlinx.coroutines.flow.Flow

@Dao
interface SettlementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: Settlement): Long

    @Query("SELECT * FROM settlements WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getSettlementsForGroup(groupId: Long): Flow<List<Settlement>>

    @Query("SELECT * FROM settlements WHERE groupId = :groupId ORDER BY createdAt DESC")
    suspend fun getSettlementsForGroupSync(groupId: Long): List<Settlement>

    @Query("SELECT * FROM settlements WHERE (fromUserId = :userId OR toUserId = :userId) AND groupId = :groupId ORDER BY createdAt DESC")
    fun getSettlementsForUser(userId: String, groupId: Long): Flow<List<Settlement>>

    @Query("SELECT * FROM settlements WHERE id = :settlementId")
    suspend fun getSettlementById(settlementId: Long): Settlement?
}