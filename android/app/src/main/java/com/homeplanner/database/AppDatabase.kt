package com.homeplanner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.homeplanner.database.dao.MetadataDao
import com.homeplanner.database.dao.SyncQueueDao
import com.homeplanner.database.dao.TaskCacheDao
import com.homeplanner.database.entity.Metadata
import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.database.entity.TaskCache

@Database(
    entities = [TaskCache::class, SyncQueueItem::class, Metadata::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskCacheDao(): TaskCacheDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun metadataDao(): MetadataDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private const val DATABASE_NAME = "homeplanner_db"
        private const val STORAGE_LIMIT_BYTES = 25 * 1024 * 1024L // 25 МБ
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration() // Для разработки
                    .build()
                INSTANCE = instance
                instance
            }
        }
        
        fun getStorageLimitBytes(): Long = STORAGE_LIMIT_BYTES
    }
}

