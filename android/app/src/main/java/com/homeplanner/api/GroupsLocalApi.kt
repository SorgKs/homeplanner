package com.homeplanner.api

import com.homeplanner.model.Group
import com.homeplanner.repository.OfflineRepository

/**
 * API для работы с локальным хранилищем групп.
 *
 * Предоставляет данные групп из локального кэша с автоматическим
 * добавлением операций в очередь синхронизации.
 */
class GroupsLocalApi(private val offlineRepository: OfflineRepository) {

    suspend fun getGroupsLocal(): Result<List<Group>> = runCatching {
        offlineRepository.loadGroupsFromCache()
    }

    suspend fun createGroupLocal(group: Group): Result<Group> = runCatching {
        offlineRepository.saveGroupsToCache(listOf(group))
        addGroupToSyncQueue("create", null, group)
        group
    }

    suspend fun updateGroupLocal(groupId: Int, group: Group): Result<Group> = runCatching {
        offlineRepository.saveGroupsToCache(listOf(group))
        addGroupToSyncQueue("update", groupId, group)
        group
    }

    suspend fun deleteGroupLocal(groupId: Int): Result<Unit> = runCatching {
        offlineRepository.deleteGroupFromCache(groupId)
        addGroupToSyncQueue("delete", groupId, null)
    }

    private suspend fun addGroupToSyncQueue(action: String, groupId: Int?, group: Group?) {
        offlineRepository.addToSyncQueue(action, "group", groupId, group)
        offlineRepository.requestSync = true
    }
}
