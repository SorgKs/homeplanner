package com.homeplanner.utils

import com.homeplanner.model.Group
import com.homeplanner.model.Task
import com.homeplanner.model.User
import java.security.MessageDigest

/**
 * Утилита для вычисления SHA-256 хешей элементов для синхронизации.
 * Хеши рассчитываются по формулам из CACHE_STRATEGY_UPDATE_V1.md
 */
object HashCalculator {

    private const val ALGORITHM = "SHA-256"

    /**
     * Вычисляет SHA-256 хеш для задачи.
     * Формула: "id|title|description|taskType|recurrenceType|recurrenceInterval|intervalDays|reminderTime|groupId|enabled|completed|assignedUserIds|updatedAt"
     * null поля заменяются на пустую строку ""
     * assignedUserIds сортируется по ID и сериализуется как "id1,id2,id3"
     */
    fun calculateTaskHash(task: Task): String {
        val description = task.description ?: ""
        val recurrenceType = task.recurrenceType ?: ""
        val recurrenceInterval = task.recurrenceInterval?.toString() ?: ""
        val intervalDays = task.intervalDays?.toString() ?: ""
        val groupId = task.groupId?.toString() ?: ""
        val assignedUserIds = task.assignedUserIds.sorted().joinToString(",")

        val data = "${task.id}|${task.title}|$description|${task.taskType}|$recurrenceType|$recurrenceInterval|$intervalDays|${task.reminderTime}|$groupId|${task.enabled}|${task.completed}|$assignedUserIds|${task.updatedAt}"

        return calculateSha256(data)
    }

    /**
     * Вычисляет SHA-256 хеш для пользователя.
     * Формула: "id|name"
     */
    fun calculateUserHash(user: User): String {
        val data = "${user.id}|${user.name}"
        return calculateSha256(data)
    }

    /**
     * Вычисляет SHA-256 хеш для группы.
     * Формула: "id|name|userIds" (userIds как отсортированная строка "id1,id2,id3")
     */
    fun calculateGroupHash(group: Group): String {
        val userIds = group.userIds.sorted().joinToString(",")
        val data = "${group.id}|${group.name}|$userIds"
        return calculateSha256(data)
    }

    /**
     * Вычисляет общий SHA-256 хеш для списка индивидуальных хешей.
     * Используется для периодической сверки: конкатенация хешей в порядке возрастания ID элементов.
     */
    fun calculateCombinedHash(hashes: List<Pair<Int, String>>): String {
        val sortedHashes = hashes.sortedBy { it.first }
        val data = sortedHashes.joinToString("") { it.second }
        return calculateSha256(data)
    }

    /**
     * Вспомогательный метод для вычисления SHA-256.
     */
    private fun calculateSha256(data: String): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}