// app/src/main/java/com/example/bestsplit/data/entity/Group.kt
package com.example.bestsplit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "", // Added default value
    val description: String = "", // Added default value
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "", // User ID of creator
    val members: List<String> = listOf() // List of user IDs
) {
    // Keep existing methods
    fun toJson(): String {
        return "{\"id\":$id,\"name\":\"$name\",\"description\":\"$description\",\"createdAt\":$createdAt}"
    }

    companion object {
        fun fromJson(json: String): Group? {
            return try {
                val jsonObj = JSONObject(json)
                Group(
                    id = jsonObj.optLong("id", 0),
                    name = jsonObj.getString("name"),
                    description = jsonObj.getString("description"),
                    createdAt = jsonObj.optLong("createdAt", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

