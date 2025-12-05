package com.homeplanner.database.dao

import androidx.room.*
import com.homeplanner.database.entity.SyncQueueItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getQueueItemsByStatus(status: String, limit: Int = 100): List<SyncQueueItem>
    
    @Query("SELECT * FROM sync_queue WHERE status IN ('pending', 'failed') ORDER BY " +
           "CASE WHEN operation IN ('complete', 'uncomplete', 'delete') THEN 0 ELSE 1 END, " +
           "timestamp ASC LIMIT :limit")
    suspend fun getPendingItems(limit: Int = 100): List<SyncQueueItem>
    
    @Query("SELECT * FROM sync_queue WHERE id = :id")
    suspend fun getQueueItemById(id: Long): SyncQueueItem?
    
    @Query("SELECT * FROM sync_queue WHERE entityId = :entityId AND status = 'pending'")
    suspend fun getPendingItemsByEntityId(entityId: Int): List<SyncQueueItem>
    
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :status")
    suspend fun getCountByStatus(status: String): Int
    
    @Query("SELECT SUM(sizeBytes) FROM sync_queue")
    suspend fun getTotalSizeBytes(): Long?
    
    @Query("SELECT * FROM sync_queue WHERE sizeBytes > 0 ORDER BY sizeBytes DESC, timestamp ASC LIMIT :limit")
    suspend fun getLargestItems(limit: Int): List<SyncQueueItem>
    
    @Query("SELECT * FROM sync_queue WHERE timestamp < :cutoffTime ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getOldestItems(cutoffTime: Long, limit: Int): List<SyncQueueItem>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: SyncQueueItem): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<SyncQueueItem>)
    
    @Update
    suspend fun updateQueueItem(item: SyncQueueItem)
    
    @Query("UPDATE sync_queue SET status = :status, retryCount = :retryCount, lastRetry = :lastRetry WHERE id = :id")
    suspend fun updateQueueItemStatus(id: Long, status: String, retryCount: Int, lastRetry: Long?)
    
    @Delete
    suspend fun deleteQueueItem(item: SyncQueueItem)
    
    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteQueueItemById(id: Long)
    
    @Query("DELETE FROM sync_queue WHERE status = 'synced' AND timestamp < :cutoffTime")
    suspend fun deleteOldSyncedItems(cutoffTime: Long)
    
    @Query("DELETE FROM sync_queue")
    suspend fun deleteAllQueueItems()
    
    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getTotalCount(): Int
}

