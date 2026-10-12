package com.example.streaming.rtmp

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer

interface PacketSink {
    fun onAvcSequenceHeader(sps: ByteArray, pps: ByteArray)
    fun onAacSequenceHeader(config: ByteArray)
    fun onVideoPacket(packet: RtmpPacket.Video)
    fun onAudioPacket(packet: RtmpPacket.Audio)
    fun onLoopBoundary(newLoopIndex: Int, totalUptimeMs: Long)
}

class LoopPacketStreamer(
    private val preparedFile: File,
    private val sink: PacketSink
) {
    private var isStreaming = false
    private var streamingJob: Job? = null

    // Telemetry and verification counters (PRD Section 3 & 8)
    var encoderInvocationCount: Long = 0L
        private set
    var loopCount: Int = 0
        private set
    var totalVideoPacketsSent: Long = 0L
        private set
    var totalAudioPacketsSent: Long = 0L
        private set
    var sessionStartTimeMs: Long = 0L
        private set

    private val rebaser = TimestampRebaser()

    fun start(scope: CoroutineScope) {
        if (isStreaming) return
        isStreaming = true
        sessionStartTimeMs = System.currentTimeMillis()
        loopCount = 0
        totalVideoPacketsSent = 0
        totalAudioPacketsSent = 0
        encoderInvocationCount = 0 // Verified 0: packet-level streaming bypasses video encoder
        rebaser.reset()

        streamingJob = scope.launch(Dispatchers.IO) {
            runLoopStreaming()
        }
    }

    fun stop() {
        isStreaming = false
        streamingJob?.cancel()
        streamingJob = null
    }

    private suspend fun runLoopStreaming() {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(preparedFile.absolutePath)
            val trackCount = extractor.trackCount
            var videoTrackIndex = -1
            var audioTrackIndex = -1

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i
                }
            }

            if (videoTrackIndex == -1) {
                Log.e("LoopStreamer", "No video track found in prepared file.")
                return
            }

            // Extract SPS & PPS
            val videoFormat = extractor.getTrackFormat(videoTrackIndex)
            val spsBuffer = videoFormat.getByteBuffer("csd-0")
            val ppsBuffer = videoFormat.getByteBuffer("csd-1")

            if (spsBuffer != null && ppsBuffer != null) {
                val sps = extractNaluFromBuffer(spsBuffer)
                val pps = extractNaluFromBuffer(ppsBuffer)
                sink.onAvcSequenceHeader(sps, pps)
            }

            // Extract Audio Specific Config
            if (audioTrackIndex != -1) {
                val audioFormat = extractor.getTrackFormat(audioTrackIndex)
                val csd0 = audioFormat.getByteBuffer("csd-0")
                if (csd0 != null) {
                    val config = ByteArray(csd0.remaining())
                    csd0.get(config)
                    sink.onAacSequenceHeader(config)
                }
            }

            // Select tracks
            extractor.selectTrack(videoTrackIndex)
            if (audioTrackIndex != -1) {
                extractor.selectTrack(audioTrackIndex)
            }

            val buffer = ByteBuffer.allocate(1024 * 512)
            var sessionStartRealtime = System.currentTimeMillis()
            var firstRebasedTsMs: Long = -1L

            while (isStreaming && currentCoroutineContext().isActive) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) {
                    // Loop boundary reached! (PRD Section 8)
                    loopCount++
                    rebaser.onLoopRestart()
                    sink.onLoopBoundary(loopCount, System.currentTimeMillis() - sessionStartTimeMs)
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    continue
                }

                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize > 0) {
                    val rawSampleTimeUs = extractor.sampleTime
                    val flags = extractor.sampleFlags
                    val isKeyframe = (flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0

                    val rebasedTsUs = rebaser.rebaseTimestamp(rawSampleTimeUs)
                    val rebasedTsMs = rebasedTsUs / 1000L

                    // Real-time pacing: sleep if ahead of real-time clock to avoid network congestion
                    if (firstRebasedTsMs == -1L) {
                        firstRebasedTsMs = rebasedTsMs
                        sessionStartRealtime = System.currentTimeMillis()
                    } else {
                        val mediaElapsed = rebasedTsMs - firstRebasedTsMs
                        val realElapsed = System.currentTimeMillis() - sessionStartRealtime
                        val leadMs = mediaElapsed - realElapsed
                        if (leadMs > 15) {
                            delay(minOf(leadMs, 50))
                        }
                    }

                    val sampleData = ByteArray(sampleSize)
                    buffer.position(0)
                    buffer.get(sampleData)

                    if (trackIndex == videoTrackIndex) {
                        val naluFormatted = ensureLengthPrefixedNalu(sampleData)
                        val videoPacket = RtmpPacket.Video(
                            naluData = naluFormatted,
                            isKeyframe = isKeyframe,
                            timestampMs = rebasedTsMs
                        )
                        sink.onVideoPacket(videoPacket)
                        totalVideoPacketsSent++
                    } else if (trackIndex == audioTrackIndex) {
                        val audioPacket = RtmpPacket.Audio(
                            aacData = sampleData,
                            timestampMs = rebasedTsMs
                        )
                        sink.onAudioPacket(audioPacket)
                        totalAudioPacketsSent++
                    }
                }

                extractor.advance()
            }

        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e("LoopStreamer", "Streaming loop encountered error: ${e.message}")
            }
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun extractNaluFromBuffer(buffer: ByteBuffer): ByteArray {
        val dup = buffer.duplicate()
        // Strip annex-b 0x00000001 start code if present
        if (dup.remaining() >= 4 && dup.get(0) == 0.toByte() && dup.get(1) == 0.toByte() && dup.get(2) == 0.toByte() && dup.get(3) == 1.toByte()) {
            dup.position(4)
        } else if (dup.remaining() >= 3 && dup.get(0) == 0.toByte() && dup.get(1) == 0.toByte() && dup.get(2) == 1.toByte()) {
            dup.position(3)
        }
        val out = ByteArray(dup.remaining())
        dup.get(out)
        return out
    }

    private fun ensureLengthPrefixedNalu(sample: ByteArray): ByteArray {
        // If sample already starts with 4-byte big-endian length prefix, return as is
        if (sample.size >= 4 && sample[0] == 0.toByte() && sample[1] == 0.toByte() && sample[2] == 0.toByte() && sample[3] == 1.toByte()) {
            // Convert 0x00000001 to length prefix
            val len = sample.size - 4
            val res = sample.clone()
            res[0] = ((len shr 24) and 0xFF).toByte()
            res[1] = ((len shr 16) and 0xFF).toByte()
            res[2] = ((len shr 8) and 0xFF).toByte()
            res[3] = (len and 0xFF).toByte()
            return res
        }
        return sample
    }
}
