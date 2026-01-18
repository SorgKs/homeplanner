package com.homeplanner.sync

import com.homeplanner.api.ServerSyncApi
import com.homeplanner.model.Task
import com.homeplanner.model.User
import com.homeplanner.model.Group
import org.json.JSONArray
import org.json.JSONObject

/**
 * Разрешает конфликты синхронизации согласно алгоритмам из CACHE_STRATEGY_UPDATE_V1.md
 */
class ConflictResolver {

    companion object {
        private const val ALGORITHM_LAST_CHANGE_WINS = "last_change_wins"
        private const val ALGORITHM_RECALCULATE = "recalculate"
        private const val ALGORITHM_CREATION_PRIORITY = "creation_priority"
    }

    /**
     * Разрешает конфликты на основе списка различий от сервера.
     * @param differences JSONArray различий от /api/v0.3/sync/hash-check
     * @param serverApi API для получения данных сервера
     * @param localTasks Локальные задачи
     * @param localUsers Локальные пользователи
     * @param localGroups Локальные группы
     * @return Список разрешенных задач для обновления локального хранилища
     */
    suspend fun resolveConflicts(
        differences: JSONArray,
        serverApi: ServerSyncApi,
        localTasks: List<Task>,
        localUsers: List<User>,
        localGroups: List<Group>
    ): List<Task> {
        val resolvedTasks = mutableListOf<Task>()

        // Группируем различия по типу
        val taskDifferences = mutableListOf<Triple<String, Int, String>>() // entity_type, id, server_hash
        val userDifferences = mutableListOf<Triple<String, Int, String>>()
        val groupDifferences = mutableListOf<Triple<String, Int, String>>()

        for (i in 0 until differences.length()) {
            val diff = differences.getJSONObject(i)
            val entityType = diff.getString("entity_type")
            val id = diff.getInt("id")
            val serverHash = diff.getString("server_hash")

            when (entityType) {
                "task" -> taskDifferences.add(Triple(entityType, id, serverHash))
                "user" -> userDifferences.add(Triple(entityType, id, serverHash))
                "group" -> groupDifferences.add(Triple(entityType, id, serverHash))
            }
        }

        // Получаем полные данные с сервера для задач
        if (taskDifferences.isNotEmpty()) {
            val serverTasks = getServerTasks(serverApi, taskDifferences.map { it.second })
            resolvedTasks.addAll(resolveTaskConflicts(serverTasks, localTasks))
        }

        // Для пользователей и групп - аналогично, но пока фокусируемся на задачах
        // TODO: Добавить разрешение для пользователей и групп

        return resolvedTasks
    }

    private suspend fun getServerTasks(serverApi: ServerSyncApi, taskIds: List<Int>): List<Task> {
        // Получаем полное состояние задач с сервера
        return try {
            val response = serverApi.getFullState("task").getOrThrow()
            val jsonArray = JSONArray(response)
            val tasks = mutableListOf<Task>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                // Фильтруем только нужные ID
                if (taskIds.contains(json.getInt("id"))) {
                    tasks.add(parseTaskFromJson(json))
                }
            }
            tasks
        } catch (e: Exception) {
            // Если не удается получить полное состояние, пробуем получить по отдельности
            taskIds.mapNotNull { id ->
                try {
                    serverApi.getTask(id).getOrThrow()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun parseTaskFromJson(json: JSONObject): Task {
        // Парсим JSON в Task (упрощенная версия toTask)
        val id = json.getInt("id")
        val title = json.getString("title")
        val description = if (json.isNull("description")) null else json.optString("description")
        val taskType = json.getString("task_type")
        val recurrenceType = if (json.isNull("recurrence_type")) null else json.optString("recurrence_type")
        val recurrenceInterval = if (json.isNull("recurrence_interval")) null else json.getInt("recurrence_interval")
        val intervalDays = if (json.isNull("interval_days")) null else json.getInt("interval_days")
        val reminderTime = json.getString("reminder_time")
        val groupId = if (json.isNull("group_id")) null else json.getInt("group_id")
        val enabled = if (json.isNull("enabled")) true else json.getBoolean("enabled")
        val completed = if (json.isNull("completed")) false else json.getBoolean("completed")
        val assignedUserIds = mutableListOf<Int>()
        if (json.has("assigned_user_ids") && !json.isNull("assigned_user_ids")) {
            val idsArray = json.getJSONArray("assigned_user_ids")
            for (i in 0 until idsArray.length()) {
                assignedUserIds.add(idsArray.getInt(i))
            }
        }
        val alarm = if (json.isNull("alarm")) false else json.getBoolean("alarm")
        val updatedAt = if (json.isNull("updated_at")) System.currentTimeMillis() else parseIsoDateTime(json.optString("updated_at"))
        val createdAt = if (json.isNull("created_at")) System.currentTimeMillis() else parseIsoDateTime(json.optString("created_at"))

        return Task(
            id = id,
            title = title,
            description = description,
            taskType = taskType,
            recurrenceType = recurrenceType,
            recurrenceInterval = recurrenceInterval,
            intervalDays = intervalDays,
            reminderTime = reminderTime,
            groupId = groupId,
            enabled = enabled,
            completed = completed,
            assignedUserIds = assignedUserIds,
            alarm = alarm,
            updatedAt = updatedAt,
            lastAccessed = System.currentTimeMillis(),
            lastShownAt = null,
            createdAt = createdAt
        )
    }

    private fun parseIsoDateTime(dateTimeStr: String?): Long {
        if (dateTimeStr.isNullOrEmpty()) return System.currentTimeMillis()
        return try {
            // Простой парсер для ISO datetime
            java.time.Instant.parse(dateTimeStr).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun resolveTaskConflicts(serverTasks: List<Task>, localTasks: List<Task>): List<Task> {
        val resolved = mutableListOf<Task>()

        // Создаем мапы для быстрого доступа
        val localTaskMap = localTasks.associateBy { it.id }
        val serverTaskMap = serverTasks.associateBy { it.id }

        // Обрабатываем все ID из обоих источников
        val allIds = (localTaskMap.keys + serverTaskMap.keys).toSet()

        for (id in allIds) {
            val localTask = localTaskMap[id]
            val serverTask = serverTaskMap[id]

            val resolvedTask = when {
                localTask != null && serverTask != null -> {
                    // Обе версии существуют - разрешаем конфликт
                    resolveTaskConflict(localTask, serverTask)
                }
                localTask != null && serverTask == null -> {
                    // Задача есть только локально - сохраняем (создание приоритетнее удаления)
                    localTask
                }
                localTask == null && serverTask != null -> {
                    // Задача есть только на сервере - берем с сервера
                    serverTask
                }
                else -> null
            }

            resolvedTask?.let { resolved.add(it) }
        }

        return resolved
    }

    /**
     * Разрешает конфликт для задачи.
     * @param localTask Локальная версия задачи
     * @param serverTask Серверная версия задачи
     * @return Разрешенная задача
     */
    fun resolveTaskConflict(localTask: Task, serverTask: Task): Task {
        // Определяем тип конфликта по измененным полям
        val conflictType = determineConflictType(localTask, serverTask)

        return when (conflictType) {
            "completed" -> resolveCompletedConflict(localTask, serverTask)
            "reminderTime" -> resolveReminderTimeConflict(localTask, serverTask)
            "recurrence" -> resolveRecurrenceConflict(localTask, serverTask)
            "description" -> resolveDescriptionConflict(localTask, serverTask)
            "existence" -> resolveExistenceConflict(localTask, serverTask)
            else -> resolveGeneralConflict(localTask, serverTask)
        }
    }

    private fun determineConflictType(localTask: Task, serverTask: Task): String {
        // Определяем тип конфликта по измененным полям
        val changedFields = mutableListOf<String>()

        if (localTask.completed != serverTask.completed) changedFields.add("completed")
        if (localTask.reminderTime != serverTask.reminderTime) changedFields.add("reminderTime")
        if (localTask.recurrenceType != serverTask.recurrenceType ||
            localTask.recurrenceInterval != serverTask.recurrenceInterval ||
            localTask.intervalDays != serverTask.intervalDays) changedFields.add("recurrence")
        if (localTask.title != serverTask.title ||
            localTask.description != serverTask.description ||
            localTask.taskType != serverTask.taskType ||
            localTask.groupId != serverTask.groupId ||
            !localTask.assignedUserIds.containsAll(serverTask.assignedUserIds) ||
            !serverTask.assignedUserIds.containsAll(localTask.assignedUserIds)) changedFields.add("description")

        return when {
            changedFields.size == 1 -> changedFields.first()
            changedFields.contains("completed") && changedFields.size == 1 -> "completed"
            changedFields.contains("reminderTime") && changedFields.size == 1 -> "reminderTime"
            changedFields.contains("recurrence") && changedFields.size == 1 -> "recurrence"
            changedFields.contains("description") -> "description"
            else -> "general"
        }
    }

    private fun resolveCompletedConflict(localTask: Task, serverTask: Task): Task {
        // "Последнее изменение побеждает"
        return if (localTask.updatedAt > serverTask.updatedAt) localTask else serverTask
    }

    private fun resolveReminderTimeConflict(localTask: Task, serverTask: Task): Task {
        // "Пересчет заново" - сервер пересчитывает
        return serverTask // Предполагаем, что сервер уже пересчитал
    }

    private fun resolveRecurrenceConflict(localTask: Task, serverTask: Task): Task {
        // "Последнее изменение побеждает"
        return if (localTask.updatedAt > serverTask.updatedAt) localTask else serverTask
    }

    private fun resolveDescriptionConflict(localTask: Task, serverTask: Task): Task {
        // "Последнее изменение побеждает"
        return if (localTask.updatedAt > serverTask.updatedAt) localTask else serverTask
    }

    private fun resolveExistenceConflict(localTask: Task, serverTask: Task): Task {
        // "Создание имеет приоритет над удалением"
        // Если задача создана локально (есть локально, нет на сервере) - сохранить локальную
        // Если задача удалена локально (есть на сервере, нет локально) - сохранить серверную
        // Но поскольку обе задачи переданы, это не случай существования, а обычный конфликт
        // Возвращаем задачу с более поздним updatedAt
        return if (localTask.updatedAt > serverTask.updatedAt) localTask else serverTask
    }

    private fun resolveGeneralConflict(localTask: Task, serverTask: Task): Task {
        // Поэлементный анализ - применяем соответствующие алгоритмы для каждого поля
        val localNewer = localTask.updatedAt > serverTask.updatedAt

        return localTask.copy(
            // Completed: last change wins
            completed = if (localNewer) localTask.completed else serverTask.completed,

            // ReminderTime: recalculate (assume server has recalculated)
            reminderTime = serverTask.reminderTime,

            // Recurrence: last change wins
            recurrenceType = if (localNewer) localTask.recurrenceType else serverTask.recurrenceType,
            recurrenceInterval = if (localNewer) localTask.recurrenceInterval else serverTask.recurrenceInterval,
            intervalDays = if (localNewer) localTask.intervalDays else serverTask.intervalDays,

            // Description fields: last change wins
            title = if (localNewer) localTask.title else serverTask.title,
            description = if (localNewer) localTask.description else serverTask.description,
            taskType = if (localNewer) localTask.taskType else serverTask.taskType,
            groupId = if (localNewer) localTask.groupId else serverTask.groupId,
            assignedUserIds = if (localNewer) localTask.assignedUserIds else serverTask.assignedUserIds,

            // Update updatedAt to the latest
            updatedAt = maxOf(localTask.updatedAt, serverTask.updatedAt)
        )
    }
}