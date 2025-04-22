// app/src/main/java/com/example/bestsplit/data/entity/Expense.kt
package com.example.bestsplit.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.bestsplit.data.database.Converters

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = Group::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(Converters::class)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val description: String,
    val amount: Double,
    val paidBy: String, // User ID of payer
    val paidFor: Map<String, Double>, // Map of user IDs to amounts
    val createdAt: Long = System.currentTimeMillis()
)