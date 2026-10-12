package com.example.data.db

import androidx.room.*
import com.example.data.model.StreamHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamHistoryDao {
    @Query("SELECT * FROM stream_history ORDER BY startTime DESC")
    fun getAllHistory(): Flow<List<StreamHistoryEntity>>

    @Query("SELECT * FROM stream_history WHERE destinationId = :destinationId ORDER BY startTime DESC")
    fun getHistoryForDestination(destinationId: Long): Flow<List<StreamHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: StreamHistoryEntity): Long

    @Update
    suspend fun updateHistory(history: StreamHistoryEntity)

    @Query("DELETE FROM stream_history")
    suspend fun clearAllHistory()
}
