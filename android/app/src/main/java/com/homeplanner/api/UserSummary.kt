package com.homeplanner.api

data class UserSummary(
    val id: Int,
    val name: String,
    val email: String?,
    val role: String,
    val isActive: Boolean
)