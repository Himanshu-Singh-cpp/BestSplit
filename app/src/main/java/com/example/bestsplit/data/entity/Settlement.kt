package com.example.bestsplit.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "settlements",
    foreignKeys = [
        ForeignKey(
            entity = Group::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Settlement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long = 0,
    val fromUserId: String = "",  // User who paid
    val toUserId: String = "",    // User who received
    val amount: Double = 0.0,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    // No-argument constructor required for Firestore
    constructor() : this(0, 0, "", "", 0.0, "", System.currentTimeMillis())
}