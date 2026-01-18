package com.homeplanner.database.dao

import androidx.room.*
import com.homeplanner.database.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY lastAccessed DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskEntity?

    @Query("SELECT * FROM tasks WHERE reminderTime >= :startDate AND reminderTime <= :endDate")
    suspend fun getTasksByDateRange(startDate: String, endDate: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE taskType = :taskType")
    suspend fun getTasksByType(taskType: String): List<TaskEntity>

    // Для политики retention: удаляем/подчищаем только неактуальные задачи.
    // Актуальные (enabled = 1) должны храниться в оффлайн-режиме "вечно".
    @Query("SELECT * FROM tasks WHERE updatedAt < :cutoffTime AND enabled = 0 ORDER BY lastAccessed ASC LIMIT :limit")
    suspend fun getOldestTasks(cutoffTime: Long, limit: Int): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTaskCount(): Int

    @Query("SELECT SUM((LENGTH(title) + LENGTH(COALESCE(description, '')) + LENGTH(assignedUserIds)) * 2) FROM tasks")
    suspend fun getCacheSizeBytes(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET lastAccessed = :timestamp WHERE id = :id")
    suspend fun updateLastAccessed(id: Int, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    // Удаляем по времени только неактуальные задачи, чтобы enabled = 1 не терялись из оффлайн-кэша.
    @Query("DELETE FROM tasks WHERE updatedAt < :cutoffTime AND enabled = 0")
    suspend fun deleteOldTasks(cutoffTime: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}

