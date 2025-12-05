package com.homeplanner.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity для хранения метаданных приложения.
 * Используется для хранения настроек, статистики и состояния синхронизации.
 */
@Entity(tableName = "metadata")
data class Metadata(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

