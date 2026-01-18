package com.homeplanner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.homeplanner.database.dao.GroupDao
import com.homeplanner.database.dao.MetadataDao
import com.homeplanner.database.dao.SyncQueueDao
import com.homeplanner.database.dao.TaskDao
import com.homeplanner.database.dao.UserDao
import com.homeplanner.database.entity.GroupEntity
import com.homeplanner.database.entity.Metadata
import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.database.entity.TaskEntity
import com.homeplanner.database.entity.UserEntity

@Database(
    entities = [TaskEntity::class, SyncQueueItem::class, Metadata::class, UserEntity::class, GroupEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun metadataDao(): MetadataDao
    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao

    companion object {
        private val MIGRATION_5_6 = Migration(5, 6) { database ->
            // Пересоздать таблицу tasks_cache без устаревших колонок и с новой колонкой hash
            database.execSQL("""
                CREATE TABLE tasks_new (
                    id INTEGER NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    taskType TEXT NOT NULL,
                    recurrenceType TEXT,
                    recurrenceInterval INTEGER,
                    intervalDays INTEGER,
                    reminderTime TEXT NOT NULL,
                    groupId INTEGER,
                    enabled INTEGER NOT NULL,
                    completed INTEGER NOT NULL,
                    assignedUserIds TEXT NOT NULL,
                    alarm INTEGER NOT NULL DEFAULT 0,
                    hash TEXT NOT NULL DEFAULT '',
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    lastAccessed INTEGER NOT NULL DEFAULT 0,
                    lastShownAt INTEGER,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
            """)

            // Скопировать данные из старой таблицы в новую (только существующие колонки)
            database.execSQL("""
                INSERT INTO tasks_new (
                    id, title, description, taskType, recurrenceType, recurrenceInterval,
                    intervalDays, reminderTime, groupId, enabled, completed, assignedUserIds,
                    alarm, updatedAt, lastAccessed, lastShownAt, createdAt
                )
                SELECT
                    id, title, description, taskType, recurrenceType, recurrenceInterval,
                    intervalDays, reminderTime, groupId, enabled, completed, assignedUserIds,
                    alarm, updatedAt, lastAccessed, lastShownAt, createdAt
                FROM tasks_cache
            """)

            // Удалить старую таблицу
            database.execSQL("DROP TABLE tasks_cache")

            // Переименовать новую таблицу
            database.execSQL("ALTER TABLE tasks_new RENAME TO tasks")

            // Создать индексы для tasks
            database.execSQL("CREATE INDEX index_tasks_reminderTime ON tasks(reminderTime)")
            database.execSQL("CREATE INDEX index_tasks_updatedAt ON tasks(updatedAt)")
            database.execSQL("CREATE INDEX index_tasks_taskType ON tasks(taskType)")
            database.execSQL("CREATE INDEX index_tasks_groupId ON tasks(groupId)")
            database.execSQL("CREATE INDEX index_tasks_hash ON tasks(hash)")

            // Создать таблицу users
            database.execSQL("""
                CREATE TABLE users (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    hash TEXT NOT NULL
                )
            """)

            // Создать индексы для users
            database.execSQL("CREATE INDEX index_users_id ON users(id)")
            database.execSQL("CREATE INDEX index_users_name ON users(name)")

            // Создать таблицу groups
            database.execSQL("""
                CREATE TABLE groups (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    createdBy INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    userIds TEXT NOT NULL,
                    hash TEXT NOT NULL
                )
            """)

            // Создать индексы для groups
            database.execSQL("CREATE INDEX index_groups_id ON groups(id)")
            database.execSQL("CREATE INDEX index_groups_name ON groups(name)")
        }

        private val MIGRATION_6_7 = Migration(6, 7) { database ->
            // Исправить assignedUserIds: установить пустую строку для NULL значений
            database.execSQL("UPDATE tasks SET assignedUserIds = '' WHERE assignedUserIds IS NULL")
        }
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
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }
        
        fun getStorageLimitBytes(): Long = STORAGE_LIMIT_BYTES
    }
}

