package com.example.bestsplit.data.repository

import android.util.Log
import com.example.bestsplit.data.dao.SettlementDao
import com.example.bestsplit.data.entity.Settlement
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class SettlementRepository(private val settlementDao: SettlementDao) {
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "SettlementRepository"

    // Firestore collection names
    private val COLLECTION_GROUPS = "groups"
    private val SUBCOLLECTION_SETTLEMENTS = "settlements"

    fun initialize() {
        // Can be used for initial setup if needed
        Log.d(TAG, "Settlement repository initialized")
    }

    fun getSettlementsForGroup(groupId: Long): Flow<List<Settlement>> {
        return settlementDao.getSettlementsForGroup(groupId)
    }

    suspend fun getSettlementsForGroupAsList(groupId: Long): List<Settlement> {
        return settlementDao.getSettlementsForGroupSync(groupId)
    }

    suspend fun getSettlementById(settlementId: Long): Settlement? {
        return settlementDao.getSettlementById(settlementId)
    }

    suspend fun addSettlement(settlement: Settlement): Long {
        try {
            // First add to local database
            val id = settlementDao.insertSettlement(settlement)

            // Create a settlement with the generated ID if it's a new settlement
            val finalSettlement = if (settlement.id == 0L) {
                settlement.copy(id = id)
            } else {
                settlement
            }

            // Create explicit map for Firestore
            val settlementData = mapOf(
                "id" to finalSettlement.id,
                "groupId" to finalSettlement.groupId,
                "fromUserId" to finalSettlement.fromUserId,
                "toUserId" to finalSettlement.toUserId,
                "amount" to finalSettlement.amount,
                "description" to finalSettlement.description,
                "createdAt" to finalSettlement.createdAt
            )

            // Add to group subcollection
            firestore.collection(COLLECTION_GROUPS)
                .document(finalSettlement.groupId.toString())
                .collection(SUBCOLLECTION_SETTLEMENTS)
                .document(finalSettlement.id.toString())
                .set(settlementData, SetOptions.merge())
                .await()

            Log.d(TAG, "Successfully added settlement ${finalSettlement.id} to Firestore")
            return finalSettlement.id
        } catch (e: Exception) {
            Log.e(TAG, "Error adding settlement", e)
            throw e
        }
    }

    suspend fun syncSettlementsForGroup(groupId: Long) {
        try {
            Log.d(TAG, "Syncing settlements for group $groupId")

            // Fetch settlements from Firestore
            val firestoreSettlements = firestore.collection(COLLECTION_GROUPS)
                .document(groupId.toString())
                .collection(SUBCOLLECTION_SETTLEMENTS)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    try {
                        Settlement(
                            id = doc.getLong("id") ?: 0,
                            groupId = doc.getLong("groupId") ?: 0,
                            fromUserId = doc.getString("fromUserId") ?: "",
                            toUserId = doc.getString("toUserId") ?: "",
                            amount = doc.getDouble("amount") ?: 0.0,
                            description = doc.getString("description") ?: "",
                            createdAt = doc.getLong("createdAt") ?: 0
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing settlement document: ${doc.id}", e)
                        null
                    }
                }

            Log.d(TAG, "Fetched ${firestoreSettlements.size} settlements from Firestore")

            // Insert all settlements to local database
            firestoreSettlements.forEach { settlement ->
                settlementDao.insertSettlement(settlement)
            }

            Log.d(TAG, "Sync complete for group $groupId settlements")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing settlements for group $groupId", e)
        }
    }
}