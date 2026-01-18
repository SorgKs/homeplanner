package com.homeplanner.database.dao

import androidx.room.*
import com.homeplanner.database.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Int): UserEntity?

    @Query("SELECT * FROM users WHERE name LIKE '%' || :query || '%'")
    suspend fun searchUsersByName(query: String): List<UserEntity>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: Int)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}