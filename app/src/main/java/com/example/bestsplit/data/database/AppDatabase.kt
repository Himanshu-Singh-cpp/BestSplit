// app/src/main/java/com/example/bestsplit/data/database/AppDatabase.kt
package com.example.bestsplit.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.bestsplit.data.dao.ExpenseDao
import com.example.bestsplit.data.dao.GroupDao
import com.example.bestsplit.data.entity.Expense
import com.example.bestsplit.data.entity.Group


@Database(entities = [Group::class, Expense::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun expenseDao(): ExpenseDao

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