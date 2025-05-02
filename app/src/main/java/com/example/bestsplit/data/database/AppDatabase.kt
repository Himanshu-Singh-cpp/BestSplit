// app/src/main/java/com/example/bestsplit/data/database/AppDatabase.kt
package com.example.bestsplit.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.bestsplit.data.dao.ExpenseDao
import com.example.bestsplit.data.dao.GroupDao
import com.example.bestsplit.data.dao.SettlementDao
import com.example.bestsplit.data.entity.Expense
import com.example.bestsplit.data.entity.Group
import com.example.bestsplit.data.entity.Settlement

@Database(
    entities = [Group::class, Expense::class, Settlement::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun settlementDao(): SettlementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bestsplit_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}