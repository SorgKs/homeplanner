package com.homeplanner.model

data class Group(
    val id: Int,
    val name: String,
    val description: String?,
    val createdBy: Int,
    val updatedAt: Long,
    val userIds: List<Int> = emptyList()
)