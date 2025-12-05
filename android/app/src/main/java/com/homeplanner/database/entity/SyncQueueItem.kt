package com.homeplanner.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity для очереди синхронизации операций.
 * Хранит несинхронизированные CRUD операции.
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["status"]),
        Index(value = ["timestamp"]),
        Index(value = ["entityId"]),
        Index(value = ["operation"]),
        Index(value = ["sizeBytes"])
    ]
)
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val operation: String, // 'create', 'update', 'delete', 'complete', 'uncomplete'
    val entityType: String, // 'task'
    val entityId: Int?, // null для create
    
    // Данные операции (только для полных операций: create, update)
    val payload: String? = null, // JSON как строка
    val revision: Int? = null, // Только для update
    
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastRetry: Long? = null,
    val status: String = "pending", // 'pending', 'syncing', 'failed'
    val sizeBytes: Long = 0 // Размер операции в байтах
) {
    /**
     * Проверяет, является ли операция легкой (complete, uncomplete, delete).
     */
    fun isLightOperation(): Boolean {
        return operation in listOf("complete", "uncomplete", "delete")
    }
    
    /**
     * Проверяет, является ли операция полной (create, update).
     */
    fun isFullOperation(): Boolean {
        return operation in listOf("create", "update")
    }
}

