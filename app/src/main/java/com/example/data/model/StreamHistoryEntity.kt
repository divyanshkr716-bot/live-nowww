package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_history")
data class StreamHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val historyId: Long = 0,
    val destinationId: Long,
    val videoId: Long,
    val destinationName: String = "",
    val videoName: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val finalStatus: String = "STOPPED", // STOPPED, COMPLETED, ERROR
    val finalErrorCategory: String? = null,
    val measuredBitrateSummary: String? = null,
    val loopsCompleted: Int = 0,
    val totalPacketsSent: Long = 0
)
