package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)
    val videoId: Long = 0,
    val displayName: String,
    val originalUriReference: String,
    val preparedFilePath: String? = null,
    val sourceFingerprint: String,
    val preparationFingerprint: String? = null,
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Double = 0.0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val fileSize: Long = 0,
    val preparedFileSize: Long = 0,
    val preparationStatus: String = STATUS_NOT_PREPARED, // NOT_PREPARED, PREPARING, READY, FAILED
    val validationStatus: String = VALIDATION_PENDING,   // PENDING, VALID, INVALID
    val validationMessage: String = "",
    val keyframeIntervalSec: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_NOT_PREPARED = "NOT_PREPARED"
        const val STATUS_PREPARING = "PREPARING"
        const val STATUS_READY = "READY"
        const val STATUS_FAILED = "FAILED"

        const val VALIDATION_PENDING = "PENDING"
        const val VALIDATION_VALID = "VALID"
        const val VALIDATION_INVALID = "INVALID"
    }
}
