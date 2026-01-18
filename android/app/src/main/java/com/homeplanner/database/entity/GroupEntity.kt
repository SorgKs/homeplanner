package com.homeplanner.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.homeplanner.model.Group

/**
 * Entity для хранения групп в Room Database.
 */
@Entity(
    tableName = "groups",
    indices = [
        Index(value = ["id"]),
        Index(value = ["name"])
    ]
)
data class GroupEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val description: String?,
    val createdBy: Int,
    val updatedAt: Long,
    val userIds: String, // Отсортированная строка "id1,id2,id3"
    // Индивидуальный SHA-256 хеш для синхронизации
    val hash: String
) {
    fun toGroup(): Group {
        val userIdsList = if (userIds.isNotEmpty()) {
            userIds.split(",").mapNotNull { it.toIntOrNull() }
        } else {
            emptyList()
        }

        return Group(
            id = id,
            name = name,
            description = description,
            createdBy = createdBy,
            updatedAt = updatedAt,
            userIds = userIdsList
        )
    }

    companion object {
        fun fromGroup(group: Group, hash: String): GroupEntity {
            val userIdsString = group.userIds.sorted().joinToString(",")

            return GroupEntity(
                id = group.id,
                name = group.name,
                description = group.description,
                createdBy = group.createdBy,
                updatedAt = group.updatedAt,
                userIds = userIdsString,
                hash = hash
            )
        }
    }
}