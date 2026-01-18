package com.homeplanner.repository

import android.util.Log
import com.homeplanner.database.AppDatabase
import com.homeplanner.database.entity.GroupEntity
import com.homeplanner.model.Group
import com.homeplanner.utils.HashCalculator
import kotlinx.coroutines.flow.first

/**
 * Репозиторий для работы с группами в локальном хранилище.
 */
class GroupRepository(
    private val db: AppDatabase,
    private val context: android.content.Context
) {
    private val groupDao = db.groupDao()

    companion object {
        private const val TAG = "GroupRepository"
    }

    suspend fun saveGroupsToCache(groups: List<Group>): Result<Unit> {
        return try {
            Log.d(TAG, "saveGroupsToCache: saving ${groups.size} groups")
            val groupEntities = groups.map { group ->
                val hash = HashCalculator.calculateGroupHash(group)
                GroupEntity.fromGroup(group, hash)
            }
            groupDao.insertGroups(groupEntities)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving groups to cache", e)
            Result.failure(e)
        }
    }

    suspend fun loadGroupsFromCache(): List<Group> {
        return try {
            val allGroupEntities = groupDao.getAllGroups().first()
            Log.d(TAG, "loadGroupsFromCache: loaded ${allGroupEntities.size} groups from database")
            allGroupEntities.map { it.toGroup() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading groups from cache", e)
            emptyList()
        }
    }

    suspend fun getGroupFromCache(id: Int): Group? {
        return try {
            val groupEntity = groupDao.getGroupById(id)
            groupEntity?.toGroup()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting group from cache", e)
            null
        }
    }

    suspend fun searchGroupsByName(query: String): List<Group> {
        return try {
            val groupEntities = groupDao.searchGroupsByName(query)
            groupEntities.map { it.toGroup() }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching groups", e)
            emptyList()
        }
    }

    suspend fun deleteGroupFromCache(id: Int) {
        try {
            groupDao.deleteGroupById(id)
            Log.d(TAG, "Deleted group from cache: id=$id")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting group from cache", e)
        }
    }

    suspend fun getCachedGroupsCount(): Int {
        return try {
            groupDao.getGroupCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached groups count", e)
            0
        }
    }

    suspend fun clearAllGroups() {
        try {
            groupDao.deleteAllGroups()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing groups", e)
        }
    }
}