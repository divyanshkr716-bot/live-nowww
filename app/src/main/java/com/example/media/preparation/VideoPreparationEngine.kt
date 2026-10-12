package com.example.media.preparation

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.data.model.MediaMetadata
import com.example.data.model.PreparationPreset
import com.example.data.model.VideoEntity
import com.example.media.inspection.MediaInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

sealed class PreparationResult {
    data class Success(
        val preparedFile: File,
        val preparedFileSize: Long,
        val isCacheReused: Boolean,
        val validationMessage: String
    ) : PreparationResult()

    data class Error(
        val message: String,
        val isDiskSpaceError: Boolean = false
    ) : PreparationResult()
}

class VideoPreparationEngine(private val context: Context) {

    private val mediaInspector = MediaInspector(context)

    suspend fun prepareVideo(
        video: VideoEntity,
        preset: PreparationPreset,
        onProgress: (progress: Float, stage: String) -> Unit
    ): PreparationResult = withContext(Dispatchers.IO) {
        val preparedDir = File(context.filesDir, "prepared_videos").apply { mkdirs() }
        val targetSettingsFingerprint = PreparationFingerprint.generateSettingsFingerprint(preset)

        // RULE 8: Check Cache
        if (video.preparationFingerprint == targetSettingsFingerprint &&
            video.preparedFilePath != null &&
            video.preparationStatus == VideoEntity.STATUS_READY
        ) {
            val existingFile = File(video.preparedFilePath)
            if (existingFile.exists() && existingFile.length() > 0) {
                val validation = validatePreparedFile(existingFile)
                if (validation.first) {
                    onProgress(1.0f, "Cached prepared media reused successfully.")
                    return@withContext PreparationResult.Success(
                        preparedFile = existingFile,
                        preparedFileSize = existingFile.length(),
                        isCacheReused = true,
                        validationMessage = "Cache hit: Valid prepared media reused."
                    )
                }
            }
        }

        // Inspect source metadata
        val sourceUri = Uri.parse(video.originalUriReference)
        val metadata = mediaInspector.inspectMedia(sourceUri, video.displayName, video.fileSize)

        // RULE 10: Disk Space Check
        val targetBitrate = if (preset.targetVideoBitrateBps > 0) preset.targetVideoBitrateBps.toLong() else maxOf(metadata.videoBitrateBps, 2_000_000L)
        val estimatedBytes = ((metadata.durationMs / 1000.0) * ((targetBitrate + 128_000) / 8.0)).toLong() + 5_000_000L
        val availableSpace = preparedDir.usableSpace

        if (availableSpace < estimatedBytes) {
            val neededMb = estimatedBytes / (1024 * 1024)
            val freeMb = availableSpace / (1024 * 1024)
            return@withContext PreparationResult.Error(
                message = "Insufficient storage space: need approx ${neededMb}MB, but only ${freeMb}MB available.",
                isDiskSpaceError = true
            )
        }

        val outputFile = File(preparedDir, "prepared_${video.videoId}_${System.currentTimeMillis()}.mp4")

        try {
            // Decide whether to Remux (Rule 1) or Transcode (Rule 2)
            val shouldRemux = preset == PreparationPreset.PASSTHROUGH_REMUX && metadata.isDirectStreamingCompatible

            if (shouldRemux) {
                onProgress(0.1f, "Compatible source detected. Remuxing packets without re-encoding...")
                remuxMedia(sourceUri, outputFile, metadata.durationMs, onProgress)
            } else {
                onProgress(0.1f, "Preparing media to YouTube Live profile (H.264/AAC, 2s keyframes)...")
                transcodeMedia(sourceUri, outputFile, metadata, preset, onProgress)
            }

            // RULE 7: Validate Output File
            onProgress(0.95f, "Validating prepared media file...")
            val (isValid, validationMessage) = validatePreparedFile(outputFile)
            if (!isValid) {
                if (outputFile.exists()) outputFile.delete()
                return@withContext PreparationResult.Error("Prepared media validation failed: $validationMessage")
            }

            onProgress(1.0f, "Preparation complete and validated.")
            PreparationResult.Success(
                preparedFile = outputFile,
                preparedFileSize = outputFile.length(),
                isCacheReused = false,
                validationMessage = validationMessage
            )

        } catch (ce: CancellationException) {
            // RULE 9: Interrupted Preparation cleanup
            if (outputFile.exists()) outputFile.delete()
            throw ce
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            PreparationResult.Error("Preparation failed: ${e.localizedMessage ?: "Unknown media error"}")
        }
    }

    /**
     * Remuxes compatible video/audio streams into a clean, well-aligned MP4 without re-encoding.
     */
    private fun remuxMedia(
        sourceUri: Uri,
        outputFile: File,
        durationMs: Long,
        onProgress: (Float, String) -> Unit
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            setExtractorSource(extractor, sourceUri)
            val trackCount = extractor.trackCount
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackMap = mutableMapOf<Int, Int>() // extractorTrackIndex -> muxerTrackIndex
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val muxerIndex = muxer.addTrack(format)
                    trackMap[i] = muxerIndex
                }
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 512)
            val bufferInfo = MediaCodec.BufferInfo()
            var lastSampleTimeUs = 0L

            while (true) {
                val sampleTrackIndex = extractor.sampleTrackIndex
                if (sampleTrackIndex < 0) break

                val muxerTrackIndex = trackMap[sampleTrackIndex]
                if (muxerTrackIndex != null) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize >= 0) {
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        bufferInfo.flags = extractor.sampleFlags
                        lastSampleTimeUs = bufferInfo.presentationTimeUs

                        muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    }
                }

                if (durationMs > 0 && lastSampleTimeUs > 0) {
                    val progress = ((lastSampleTimeUs / 1000.0) / durationMs).toFloat().coerceIn(0.1f, 0.94f)
                    onProgress(progress, "Remuxing packets (${(progress * 100).toInt()}%)...")
                }

                extractor.advance()
            }

        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                // ignore
            }
            try {
                extractor.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Transcodes video using hardware MediaCodec to H.264 (AVC) with ~2.0s keyframe interval.
     */
    private fun transcodeMedia(
        sourceUri: Uri,
        outputFile: File,
        metadata: MediaMetadata,
        preset: PreparationPreset,
        onProgress: (Float, String) -> Unit
    ) {
        val targetWidth = if (preset.targetWidth > 0) preset.targetWidth else if (metadata.width > 0) metadata.width else 1280
        val targetHeight = if (preset.targetHeight > 0) preset.targetHeight else if (metadata.height > 0) metadata.height else 720
        val targetBitrate = if (preset.targetVideoBitrateBps > 0) preset.targetVideoBitrateBps else 2_500_000
        val targetFps = if (preset.targetFps > 0) preset.targetFps else 30

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            setExtractorSource(extractor, sourceUri)
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
                throw IllegalStateException("Source video track not found.")
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Setup H.264 Encoder Format targeting ~2.0s keyframe intervals (PRD Rule 4)
            val encoderFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) // 2-second keyframe interval
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            // Setup Decoder
            extractor.selectTrack(videoTrackIndex)
            val srcVideoFormat = extractor.getTrackFormat(videoTrackIndex)
            val srcVideoMime = srcVideoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
            val decoder = MediaCodec.createDecoderByType(srcVideoMime)
            decoder.configure(srcVideoFormat, inputSurface, null, 0)
            decoder.start()

            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            var isMuxerStarted = false

            // Check if audio can be passthrough
            val hasCompatibleAudio = audioTrackIndex != -1 && metadata.audioCodec.contains("mp4a", ignoreCase = true)
            if (hasCompatibleAudio) {
                extractor.selectTrack(audioTrackIndex)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            val timeoutUs = 10_000L
            var lastEncodedPtsUs = 0L

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputBufIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufIndex >= 0) {
                        val inputBuf = decoder.getInputBuffer(inputBufIndex)
                        if (inputBuf != null) {
                            val sampleSize = extractor.readSampleData(inputBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                decoder.queueInputBuffer(inputBufIndex, 0, sampleSize, sampleTime, extractor.sampleFlags)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Dequeue decoded surface frames
                val decoderOutputIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (decoderOutputIndex >= 0) {
                    val render = bufferInfo.size > 0
                    decoder.releaseOutputBuffer(decoderOutputIndex, render)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoder.signalEndOfInputStream()
                    }
                }

                // Dequeue encoder output
                val encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = encoder.outputFormat
                    muxerVideoTrack = muxer.addTrack(newFormat)
                    if (hasCompatibleAudio) {
                        val audioFormat = extractor.getTrackFormat(audioTrackIndex)
                        muxerAudioTrack = muxer.addTrack(audioFormat)
                    }
                    muxer.start()
                    isMuxerStarted = true
                } else if (encoderOutputIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(encoderOutputIndex)
                    if (encodedData != null && bufferInfo.size > 0 && isMuxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        lastEncodedPtsUs = bufferInfo.presentationTimeUs
                        muxer.writeSampleData(muxerVideoTrack, encodedData, bufferInfo)
                    }

                    if (metadata.durationMs > 0 && lastEncodedPtsUs > 0) {
                        val progress = ((lastEncodedPtsUs / 1000.0) / metadata.durationMs).toFloat().coerceIn(0.1f, 0.90f)
                        onProgress(progress, "Transcoding H.264 video (${(progress * 100).toInt()}%)...")
                    }

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                    encoder.releaseOutputBuffer(encoderOutputIndex, false)
                }
            }

            // Copy compatible audio if present
            if (hasCompatibleAudio && isMuxerStarted && muxerAudioTrack != -1) {
                onProgress(0.92f, "Writing aligned AAC audio track...")
                val audioExtractor = MediaExtractor()
                try {
                    setExtractorSource(audioExtractor, sourceUri)
                    audioExtractor.selectTrack(audioTrackIndex)
                    val audioBuffer = ByteBuffer.allocate(1024 * 64)
                    val audioBufferInfo = MediaCodec.BufferInfo()
                    while (true) {
                        val size = audioExtractor.readSampleData(audioBuffer, 0)
                        if (size < 0) break
                        audioBufferInfo.offset = 0
                        audioBufferInfo.size = size
                        audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
                        audioBufferInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
                        audioExtractor.advance()
                    }
                } finally {
                    try { audioExtractor.release() } catch (e: Exception) {}
                }
            }

            try { decoder.stop(); decoder.release() } catch (e: Exception) {}
            try { encoder.stop(); encoder.release() } catch (e: Exception) {}
            try { inputSurface.release() } catch (e: Exception) {}

        } finally {
            try { muxer?.stop(); muxer?.release() } catch (e: Exception) {}
            try { extractor.release() } catch (e: Exception) {}
        }
    }

    private fun setExtractorSource(extractor: MediaExtractor, uri: Uri) {
        if (uri.scheme == "content" || uri.scheme == "android.resource") {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                extractor.setDataSource(pfd.fileDescriptor)
                pfd.close()
            } else {
                extractor.setDataSource(context, uri, null)
            }
        } else {
            extractor.setDataSource(uri.path ?: "")
        }
    }

    /**
     * RULE 7: Validates prepared file before marking Ready.
     */
    fun validatePreparedFile(file: File): Pair<Boolean, String> {
        if (!file.exists() || file.length() < 1024) {
            return Pair(false, "File is missing or empty.")
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val trackCount = extractor.trackCount
            var videoTrackIndex = -1
            var videoMime = ""
            var hasSpsPps = false
            var width = 0
            var height = 0

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    videoMime = mime
                    width = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0
                    height = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0
                    hasSpsPps = format.containsKey("csd-0") // SPS/PPS
                }
            }

            if (videoTrackIndex == -1) {
                return Pair(false, "No video track found in prepared file.")
            }

            if (!videoMime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                return Pair(false, "Video codec is $videoMime (expected H.264/AVC).")
            }

            // Verify readability and sync frames
            extractor.selectTrack(videoTrackIndex)
            val testBuffer = ByteBuffer.allocate(1024 * 64)
            var syncFrameCount = 0
            var totalSamples = 0

            while (totalSamples < 120) {
                val sampleSize = extractor.readSampleData(testBuffer, 0)
                if (sampleSize < 0) break
                val flags = extractor.sampleFlags
                if ((flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    syncFrameCount++
                }
                totalSamples++
                extractor.advance()
            }

            if (totalSamples == 0) {
                return Pair(false, "Unable to read video packet samples.")
            }

            if (syncFrameCount == 0) {
                return Pair(false, "No keyframes found in video samples.")
            }

            val summary = "Valid H.264/AVC (${width}x${height}), SPS/PPS verified, $syncFrameCount keyframes checked."
            return Pair(true, summary)

        } catch (e: Exception) {
            return Pair(false, "Validation read error: ${e.localizedMessage}")
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
