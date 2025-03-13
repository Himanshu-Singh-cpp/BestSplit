// app/src/main/java/com/example/bestsplit/BestSplitApplication.kt
package com.example.bestsplit

import android.app.Application
import com.example.bestsplit.data.database.AppDatabase

class BestSplitApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
}