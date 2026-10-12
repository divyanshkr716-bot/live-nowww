package com.example.data.db

import androidx.room.*
import com.example.data.model.StreamDestinationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamDestinationDao {
    @Query("SELECT * FROM stream_destinations ORDER BY createdAt DESC")
    fun getAllDestinations(): Flow<List<StreamDestinationEntity>>

    @Query("SELECT * FROM stream_destinations WHERE enabled = 1")
    fun getEnabledDestinations(): Flow<List<StreamDestinationEntity>>

    @Query("SELECT * FROM stream_destinations WHERE destinationId = :id")
    suspend fun getDestinationById(id: Long): StreamDestinationEntity?

    @Query("SELECT * FROM stream_destinations WHERE destinationId = :id")
    fun getDestinationFlow(id: Long): Flow<StreamDestinationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDestination(destination: StreamDestinationEntity): Long

    @Update
    suspend fun updateDestination(destination: StreamDestinationEntity)

    @Delete
    suspend fun deleteDestination(destination: StreamDestinationEntity)

    @Query("DELETE FROM stream_destinations WHERE destinationId = :id")
    suspend fun deleteDestinationById(id: Long)
}
