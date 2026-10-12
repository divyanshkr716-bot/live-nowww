package com.example.media.inspection

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.data.model.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer

class MediaInspector(private val context: Context) {

    suspend fun inspectMedia(uri: Uri, displayName: String, fileSize: Long): MediaMetadata = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var videoMime = ""
        var audioMime = ""
        var width = 0
        var height = 0
        var frameRate = 0.0
        var durationMs = 0L
        var videoBitrate = 0L
        var audioBitrate = 0L
        var audioChannels = 0
        var audioSampleRate = 0
        var hasAudio = false
        var videoTrackIndex = -1
        var audioTrackIndex = -1

        try {
            if (uri.scheme == "content" || uri.scheme == "android.resource") {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val fd: FileDescriptor = pfd.fileDescriptor
                    extractor.setDataSource(fd)
                    pfd.close()
                } else {
                    extractor.setDataSource(context, uri, null)
                }
            } else {
                extractor.setDataSource(uri.path ?: "")
            }

            val numTracks = extractor.trackCount
            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    videoMime = mime
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                        width = format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                        height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        val durUs = format.getLong(MediaFormat.KEY_DURATION)
                        durationMs = durUs / 1000
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        videoBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                    }
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i
                    audioMime = mime
                    hasAudio = true
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        audioChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        audioBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                    }
                    if (durationMs == 0L && format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000
                    }
                }
            }

            // Inspect keyframe spacing and timestamp continuity on the video track
            var measuredKeyframeIntervalSec = 0.0
            var isContinuous = true

            if (videoTrackIndex != -1) {
                extractor.selectTrack(videoTrackIndex)
                val syncTimesUs = mutableListOf<Long>()
                var prevSampleTimeUs = -1L
                val sampleBuffer = ByteBuffer.allocate(1024 * 64)
                var samplesChecked = 0

                while (samplesChecked < 300) {
                    val sampleSize = extractor.readSampleData(sampleBuffer, 0)
                    if (sampleSize < 0) break

                    val timeUs = extractor.sampleTime
                    val flags = extractor.sampleFlags

                    if (prevSampleTimeUs != -1L && timeUs < prevSampleTimeUs) {
                        isContinuous = false
                    }
                    prevSampleTimeUs = timeUs

                    if ((flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        syncTimesUs.add(timeUs)
                        if (syncTimesUs.size >= 4) break
                    }

                    samplesChecked++
                    extractor.advance()
                }

                if (syncTimesUs.size >= 2) {
                    val intervalsUs = mutableListOf<Long>()
                    for (k in 1 until syncTimesUs.size) {
                        intervalsUs.add(syncTimesUs[k] - syncTimesUs[k - 1])
                    }
                    val avgIntervalUs = intervalsUs.average()
                    measuredKeyframeIntervalSec = avgIntervalUs / 1_000_000.0
                }

                // If frameRate wasn't in metadata header, estimate from duration and samples
                if (frameRate <= 0.0 && durationMs > 0 && samplesChecked > 10) {
                    frameRate = 30.0 // Default standard
                }
            }

            val isH264 = videoMime.equals("video/avc", ignoreCase = true)
            val isAac = audioMime.equals("audio/mp4a-latm", ignoreCase = true) || !hasAudio
            val isKeyframeAcceptable = measuredKeyframeIntervalSec in 0.5..4.0 || measuredKeyframeIntervalSec == 0.0

            val isDirectStreamingCompatible = isH264 && isAac && isContinuous && isKeyframeAcceptable

            val compatibilitySummary = buildString {
                if (isDirectStreamingCompatible) {
                    append("Compatible for direct packet streaming (H.264/AAC). No video re-encoding needed.")
                } else {
                    val issues = mutableListOf<String>()
                    if (!isH264) issues.add("Video codec is $videoMime (H.264 required)")
                    if (!isAac) issues.add("Audio codec is $audioMime (AAC required)")
                    if (!isKeyframeAcceptable) issues.add("Keyframe interval (${String.format("%.1f", measuredKeyframeIntervalSec)}s) differs from target 2.0s")
                    if (!isContinuous) issues.add("Timestamps have non-monotonic jumps")
                    append("Preparation required: ").append(issues.joinToString("; "))
                }
            }

            MediaMetadata(
                uriString = uri.toString(),
                fileName = displayName,
                containerFormat = "mp4",
                videoCodec = if (videoMime.isNotEmpty()) videoMime else "Unknown",
                audioCodec = if (hasAudio) audioMime else "None (Silent)",
                width = width,
                height = height,
                frameRate = frameRate,
                durationMs = durationMs,
                videoBitrateBps = videoBitrate,
                audioBitrateBps = audioBitrate,
                audioChannels = audioChannels,
                audioSampleRate = audioSampleRate,
                hasAudioTrack = hasAudio,
                keyframeIntervalSec = measuredKeyframeIntervalSec,
                isTimestampContinuous = isContinuous,
                isDirectStreamingCompatible = isDirectStreamingCompatible,
                compatibilitySummary = compatibilitySummary,
                fileSize = fileSize
            )

        } catch (e: Exception) {
            MediaMetadata(
                uriString = uri.toString(),
                fileName = displayName,
                videoCodec = "Unreadable",
                audioCodec = "Unreadable",
                compatibilitySummary = "Error reading media: ${e.localizedMessage ?: "Unknown error"}",
                isDirectStreamingCompatible = false,
                fileSize = fileSize
            )
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
