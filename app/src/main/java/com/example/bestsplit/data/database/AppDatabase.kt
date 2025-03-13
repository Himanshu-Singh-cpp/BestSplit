// app/src/main/java/com/example/bestsplit/data/database/AppDatabase.kt
package com.example.bestsplit.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.bestsplit.data.dao.GroupDao
import com.example.bestsplit.data.entity.Group

@Database(entities = [Group::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bestsplit_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}