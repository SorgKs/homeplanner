package com.homeplanner.database.dao

import androidx.room.*
import com.homeplanner.database.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getGroupById(id: Int): GroupEntity?

    @Query("SELECT * FROM groups WHERE name LIKE '%' || :query || '%'")
    suspend fun searchGroupsByName(query: String): List<GroupEntity>

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun getGroupCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteGroupById(id: Int)

    @Query("DELETE FROM groups")
    suspend fun deleteAllGroups()
}