package com.homeplanner.database.dao

import androidx.room.*
import com.homeplanner.database.entity.Metadata

@Dao
interface MetadataDao {
    @Query("SELECT * FROM metadata WHERE key = :key")
    suspend fun getMetadata(key: String): Metadata?
    
    @Query("SELECT value FROM metadata WHERE key = :key")
    suspend fun getMetadataValue(key: String): String?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: Metadata)
    
    @Update
    suspend fun updateMetadata(metadata: Metadata)
    
    @Query("DELETE FROM metadata WHERE key = :key")
    suspend fun deleteMetadata(key: String)
    
    @Query("SELECT * FROM metadata")
    suspend fun getAllMetadata(): List<Metadata>
}

