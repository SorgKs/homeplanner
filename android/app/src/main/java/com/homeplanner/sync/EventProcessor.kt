package com.homeplanner.sync

import android.util.Log
import com.homeplanner.model.Group
import com.homeplanner.model.Task
import com.homeplanner.model.User
import com.homeplanner.repository.GroupRepository
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.repository.UserRepository

/**
 * Обрабатывает события синхронизации от сервера.
 * Применяет изменения к локальному хранилищу.
 */
class EventProcessor(
    private val offlineRepository: OfflineRepository,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository
) {
    companion object {
        private const val TAG = "EventProcessor"
    }

    /**
     * Обрабатывает событие от сервера.
     * @param eventType Тип события (create, update, delete)
     * @param entityType Тип сущности (task, user, group)
     * @param entityId ID сущности
     * @param entityData Данные сущности (JSON или объект)
     */
    suspend fun processServerEvent(
        eventType: String,
        entityType: String,
        entityId: Int,
        entityData: Any?
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Processing server event: $eventType $entityType $entityId")

            when (entityType) {
                "task" -> processTaskEvent(eventType, entityId, entityData as? Task)
                "user" -> processUserEvent(eventType, entityId, entityData as? User)
                "group" -> processGroupEvent(eventType, entityId, entityData as? Group)
                else -> {
                    Log.w(TAG, "Unknown entity type: $entityType")
                    Result.failure(IllegalArgumentException("Unknown entity type: $entityType"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing server event", e)
            Result.failure(e)
        }
    }

    private suspend fun processTaskEvent(eventType: String, entityId: Int, task: Task?): Result<Unit> {
        return when (eventType) {
            "create", "update" -> {
                if (task != null) {
                    offlineRepository.saveTasksToCache(listOf(task))
                } else {
                    Result.failure(IllegalArgumentException("Task data required for $eventType"))
                }
            }
            "delete" -> {
                offlineRepository.deleteTaskFromCache(entityId)
                Result.success(Unit)
            }
            else -> Result.failure(IllegalArgumentException("Unknown event type: $eventType"))
        }
    }

    private suspend fun processUserEvent(eventType: String, entityId: Int, user: User?): Result<Unit> {
        return when (eventType) {
            "create", "update" -> {
                if (user != null) {
                    userRepository.saveUsersToCache(listOf(user))
                } else {
                    Result.failure(IllegalArgumentException("User data required for $eventType"))
                }
            }
            "delete" -> {
                userRepository.deleteUserFromCache(entityId)
                Result.success(Unit)
            }
            else -> Result.failure(IllegalArgumentException("Unknown event type: $eventType"))
        }
    }

    private suspend fun processGroupEvent(eventType: String, entityId: Int, group: Group?): Result<Unit> {
        return when (eventType) {
            "create", "update" -> {
                if (group != null) {
                    groupRepository.saveGroupsToCache(listOf(group))
                } else {
                    Result.failure(IllegalArgumentException("Group data required for $eventType"))
                }
            }
            "delete" -> {
                groupRepository.deleteGroupFromCache(entityId)
                Result.success(Unit)
            }
            else -> Result.failure(IllegalArgumentException("Unknown event type: $eventType"))
        }
    }
}