package com.homeplanner.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.homeplanner.model.User

/**
 * Entity для хранения пользователей в Room Database.
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["id"]),
        Index(value = ["name"])
    ]
)
data class UserEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    // Индивидуальный SHA-256 хеш для синхронизации
    val hash: String
) {
    fun toUser(): User {
        return User(
            id = id,
            name = name
        )
    }

    companion object {
        fun fromUser(user: User, hash: String): UserEntity {
            return UserEntity(
                id = user.id,
                name = user.name,
                hash = hash
            )
        }
    }
}