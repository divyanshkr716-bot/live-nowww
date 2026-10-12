package com.example.data.db

import androidx.room.*
import com.example.data.model.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY createdAt DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE videoId = :id")
    suspend fun getVideoById(id: Long): VideoEntity?

    @Query("SELECT * FROM videos WHERE videoId = :id")
    fun getVideoFlow(id: Long): Flow<VideoEntity?>

    @Query("SELECT * FROM videos WHERE sourceFingerprint = :fingerprint LIMIT 1")
    suspend fun findBySourceFingerprint(fingerprint: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Delete
    suspend fun deleteVideo(video: VideoEntity)

    @Query("DELETE FROM videos WHERE videoId = :id")
    suspend fun deleteVideoById(id: Long)
}
