package com.homeplanner.sync

import com.homeplanner.api.UserSummary
import com.homeplanner.model.Task

/**
 * Результат синхронизации очереди операций.
 */
data class SyncResult(
    val successCount: Int,
    val failureCount: Int,
    val conflictCount: Int,
    val tasks: List<Task> = emptyList()
)

/**
 * Результат синхронизации кэша с сервером.
 */
data class SyncCacheResult(
    val cacheUpdated: Boolean,
    val users: List<UserSummary>? = null,
    val groups: Map<Int, String>? = null
)