package com.example.streaming.rtmp

class TimestampRebaser(
    private val nominalFrameIntervalUs: Long = 33_333L // ~30 fps
) {
    private var baseOffsetUs: Long = 0L
    private var firstSampleTimeInLoopUs: Long = -1L
    private var lastEmittedTimestampUs: Long = -1L

    /**
     * Converts a raw MediaExtractor sample presentation time (in microseconds)
     * into a monotonically increasing output timestamp across loop boundaries.
     */
    fun rebaseTimestamp(rawSampleTimeUs: Long): Long {
        if (firstSampleTimeInLoopUs == -1L) {
            firstSampleTimeInLoopUs = rawSampleTimeUs
        }

        val relativeTimeUs = maxOf(0L, rawSampleTimeUs - firstSampleTimeInLoopUs)
        var outputTimestampUs = baseOffsetUs + relativeTimeUs

        // Strictly ensure monotonicity: output timestamp must never decrease
        if (lastEmittedTimestampUs != -1L && outputTimestampUs <= lastEmittedTimestampUs) {
            outputTimestampUs = lastEmittedTimestampUs + 1_000L // Ensure at least 1ms progression
        }

        lastEmittedTimestampUs = outputTimestampUs
        return outputTimestampUs
    }

    /**
     * Called when the video reaches EOF and restarts the loop sequence.
     * Computes the new base offset so the next loop continues seamlessly.
     */
    fun onLoopRestart() {
        if (lastEmittedTimestampUs != -1L) {
            baseOffsetUs = lastEmittedTimestampUs + nominalFrameIntervalUs
        }
        firstSampleTimeInLoopUs = -1L
    }

    /**
     * Resets the entire rebaser when a new streaming session begins.
     */
    fun reset() {
        baseOffsetUs = 0L
        firstSampleTimeInLoopUs = -1L
        lastEmittedTimestampUs = -1L
    }

    val currentTimestampMs: Long
        get() = if (lastEmittedTimestampUs >= 0) lastEmittedTimestampUs / 1000L else 0L
}
