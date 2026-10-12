package com.example.data.model

data class MediaMetadata(
    val uriString: String,
    val fileName: String,
    val containerFormat: String = "mp4",
    val videoCodec: String = "",
    val audioCodec: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Double = 0.0,
    val durationMs: Long = 0,
    val videoBitrateBps: Long = 0,
    val audioBitrateBps: Long = 0,
    val audioChannels: Int = 0,
    val audioSampleRate: Int = 0,
    val hasAudioTrack: Boolean = false,
    val keyframeIntervalSec: Double = 0.0,
    val isTimestampContinuous: Boolean = true,
    val isDirectStreamingCompatible: Boolean = false,
    val compatibilitySummary: String = "",
    val fileSize: Long = 0
)

enum class StreamState {
    IDLE,
    PREPARING,
    READY,
    CONNECTING,
    LIVE,
    RECONNECTING,
    STOPPING,
    STOPPED,
    ERROR;

    val isStreamingActive: Boolean
        get() = this == CONNECTING || this == LIVE || this == RECONNECTING
}

enum class PreparationPreset(
    val title: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val targetFps: Int,
    val targetVideoBitrateBps: Int,
    val targetKeyframeIntervalSec: Int = 2
) {
    PASSTHROUGH_REMUX("Passthrough / Remux (Source Quality)", 0, 0, 0, 0, 2),
    YOUTUBE_1080P("1080p Full HD (4500 kbps, 30fps, 2s GOP)", 1920, 1080, 30, 4_500_000, 2),
    YOUTUBE_720P("720p HD (2500 kbps, 30fps, 2s GOP)", 1280, 720, 30, 2_500_000, 2),
    YOUTUBE_480P("480p SD - Battery Saver (1200 kbps, 30fps, 2s GOP)", 854, 480, 30, 1_200_000, 2)
}

enum class ErrorCategory {
    KEYFRAME_ERROR,
    BITRATE_WARNING,
    AUDIO_INITIALIZATION_ERROR,
    MULTIPLE_INGESTION_ERROR,
    NETWORK_ERROR,
    AUTHENTICATION_ERROR,
    FILE_ERROR,
    GENERAL_ERROR
}

data class DiagnosticError(
    val category: ErrorCategory,
    val userExplanation: String,
    val redactedDetails: String,
    val canRetry: Boolean,
    val correctiveAction: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DiagnosticLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val isError: Boolean = false
)
