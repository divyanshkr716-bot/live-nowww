package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_destinations")
data class StreamDestinationEntity(
    @PrimaryKey(autoGenerate = true)
    val destinationId: Long = 0,
    val displayName: String,
    val ingestUrl: String = "rtmp://a.rtmp.youtube.com/live2",
    val encryptedStreamKeyReference: String,
    val selectedVideoId: Long? = null,
    val enabled: Boolean = true,
    val maxRetries: Int = 5,
    val backoffInitialSec: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
