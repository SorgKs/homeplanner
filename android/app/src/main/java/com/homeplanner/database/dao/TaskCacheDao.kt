package com.homeplanner.database.dao

import androidx.room.*
import com.homeplanner.database.entity.TaskCache
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskCacheDao {
    @Query("SELECT * FROM tasks_cache ORDER BY lastAccessed DESC")
    fun getAllTasks(): Flow<List<TaskCache>>
    
    @Query("SELECT * FROM tasks_cache WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskCache?
    
    @Query("SELECT * FROM tasks_cache WHERE reminderTime >= :startDate AND reminderTime <= :endDate")
    suspend fun getTasksByDateRange(startDate: String, endDate: String): List<TaskCache>
    
    @Query("SELECT * FROM tasks_cache WHERE taskType = :taskType")
    suspend fun getTasksByType(taskType: String): List<TaskCache>
    
    // Для политики retention: удаляем/подчищаем только неактуальные задачи.
    // Актуальные (enabled = 1) должны храниться в оффлайн-режиме "вечно".
    @Query("SELECT * FROM tasks_cache WHERE updatedAt < :cutoffTime AND enabled = 0 ORDER BY lastAccessed ASC LIMIT :limit")
    suspend fun getOldestTasks(cutoffTime: Long, limit: Int): List<TaskCache>
    
    @Query("SELECT COUNT(*) FROM tasks_cache")
    suspend fun getTaskCount(): Int
    
    @Query("SELECT SUM((LENGTH(title) + LENGTH(COALESCE(description, '')) + LENGTH(assignedUserIds)) * 2) FROM tasks_cache")
    suspend fun getCacheSizeBytes(): Long?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskCache)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskCache>)
    
    @Update
    suspend fun updateTask(task: TaskCache)
    
    @Query("UPDATE tasks_cache SET lastAccessed = :timestamp WHERE id = :id")
    suspend fun updateLastAccessed(id: Int, timestamp: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun deleteTask(task: TaskCache)
    
    @Query("DELETE FROM tasks_cache WHERE id = :id")
    suspend fun deleteTaskById(id: Int)
    
    // Удаляем по времени только неактуальные задачи, чтобы enabled = 1 не терялись из оффлайн-кэша.
    @Query("DELETE FROM tasks_cache WHERE updatedAt < :cutoffTime AND enabled = 0")
    suspend fun deleteOldTasks(cutoffTime: Long)
    
    @Query("DELETE FROM tasks_cache")
    suspend fun deleteAllTasks()
}

