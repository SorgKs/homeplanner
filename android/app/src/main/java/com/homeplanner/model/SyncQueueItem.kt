package com.homeplanner.model

data class SyncQueueItem(
    val id: Long,
    val operation: String,
    val entityType: String,
    val entityId: Int?,
    val payload: String?,
    val timestamp: Long,
    val retryCount: Int,
    val lastRetry: Long?,
    val status: String,
    val sizeBytes: Long
)