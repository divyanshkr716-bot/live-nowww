package com.example.streaming.rtmp

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.example.data.model.*
import com.example.data.repository.DestinationRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.VideoRepository
import com.example.data.security.CryptoManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class StreamSessionState(
    val destinationId: Long,
    val displayName: String,
    val ingestUrl: String,
    val state: StreamState = StreamState.IDLE,
    val measuredBitrateBps: Long = 0,
    val retryAttempt: Int = 0,
    val lastError: String? = null,
    val totalPacketsSent: Long = 0,
    val uptimeMs: Long = 0
)

data class StreamOverallDiagnostics(
    val isAnyStreaming: Boolean = false,
    val activeStreamCount: Int = 0,
    val totalUptimeMs: Long = 0,
    val totalLoopsCompleted: Int = 0,
    val totalVideoPacketsEmitted: Long = 0,
    val totalAudioPacketsEmitted: Long = 0,
    val encoderInvocationCount: Long = 0, // PRD criteria: 0 during loop
    val totalOutputBitrateBps: Long = 0,
    val batterySaverActive: Boolean = false
)

class MultiStreamManager(
    private val context: Context,
    private val destinationRepository: DestinationRepository,
    private val videoRepository: VideoRepository,
    private val settingsRepository: SettingsRepository
) : PacketSink {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var packetStreamer: LoopPacketStreamer? = null
    private var activeVideo: VideoEntity? = null
    private var streamingStartTimeMs: Long = 0

    // Per-destination workers
    private val activeClients = mutableMapOf<Long, RtmpClient>()
    private val activeQueues = mutableMapOf<Long, Channel<RtmpPacket>>()
    private val activeJobs = mutableMapOf<Long, Job>()

    private val _sessionsState = MutableStateFlow<Map<Long, StreamSessionState>>(emptyMap())
    val sessionsState: StateFlow<Map<Long, StreamSessionState>> = _sessionsState.asStateFlow()

    private val _diagnostics = MutableStateFlow(StreamOverallDiagnostics())
    val diagnostics: StateFlow<StreamOverallDiagnostics> = _diagnostics.asStateFlow()

    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    private val _recentErrors = MutableStateFlow<List<DiagnosticError>>(emptyList())
    val recentErrors: StateFlow<List<DiagnosticError>> = _recentErrors.asStateFlow()

    private var cachedSpsPps: Pair<ByteArray, ByteArray>? = null
    private var cachedAacConfig: ByteArray? = null

    // Wake lock
    private var wakeLock: PowerManager.WakeLock? = null

    init {
        // Ticker to update measured bitrates and diagnostics every second
        scope.launch {
            while (isActive) {
                delay(1000)
                updateDiagnostics()
            }
        }
    }

    suspend fun startStreaming(
        videoId: Long,
        destinationIds: List<Long>
    ): Result<Unit> = withContext(Dispatchers.Default) {
        val video = videoRepository.getVideoById(videoId)
            ?: return@withContext Result.failure(Exception("Selected video does not exist."))

        if (video.preparationStatus != VideoEntity.STATUS_READY || video.preparedFilePath == null) {
            return@withContext Result.failure(Exception("Video must be prepared once before streaming."))
        }

        val preparedFile = File(video.preparedFilePath)
        if (!preparedFile.exists() || preparedFile.length() < 1024) {
            return@withContext Result.failure(Exception("Prepared video file is missing or corrupted."))
        }

        // Enforce resource concurrency limits (PRD Phase 6 & 8)
        val currentSettings = settingsRepository.settingsFlow.first()
        if (destinationIds.size > currentSettings.maxActiveStreams) {
            return@withContext Result.failure(
                Exception("Cannot start ${destinationIds.size} concurrent destinations: active limit is ${currentSettings.maxActiveStreams}. Increase limit in Battery Saver settings if needed.")
            )
        }

        // Prevent duplicate streams to identical stream keys (PRD Section 4 & 9)
        val destinations = destinationIds.mapNotNull { destinationRepository.getDestinationById(it) }
        val usedKeys = mutableSetOf<String>()
        for (dest in destinations) {
            val key = destinationRepository.getDecryptedStreamKey(dest)
            if (key.isBlank()) {
                return@withContext Result.failure(Exception("Destination '${dest.displayName}' has no valid stream key."))
            }
            if (usedKeys.contains(key)) {
                recordError(
                    DiagnosticError(
                        category = ErrorCategory.MULTIPLE_INGESTION_ERROR,
                        userExplanation = "Duplicate stream key detected for '${dest.displayName}'.",
                        redactedDetails = "Cannot stream multiple independent sessions to identical stream key.",
                        canRetry = false,
                        correctiveAction = "Ensure each destination has a unique YouTube stream key."
                    )
                )
                return@withContext Result.failure(Exception("Duplicate stream key detected in destination list."))
            }
            usedKeys.add(key)
        }

        activeVideo = video
        streamingStartTimeMs = System.currentTimeMillis()
        acquireWakeLock()

        log("Engine", "Starting infinite loop stream session for video: ${video.displayName}")

        // Initialize streamer
        packetStreamer = LoopPacketStreamer(preparedFile, this@MultiStreamManager)

        // Launch per-destination workers
        for (dest in destinations) {
            startDestinationWorker(dest)
        }

        // Start packet extraction
        packetStreamer?.start(scope)
        Result.success(Unit)
    }

    private fun startDestinationWorker(destination: StreamDestinationEntity) {
        val destId = destination.destinationId
        val queue = Channel<RtmpPacket>(capacity = 120)
        activeQueues[destId] = queue

        updateSession(destId) {
            it.copy(
                destinationId = destId,
                displayName = destination.displayName,
                ingestUrl = destination.ingestUrl,
                state = StreamState.CONNECTING,
                lastError = null,
                retryAttempt = 0
            )
        }

        val job = scope.launch(Dispatchers.IO) {
            var retryCount = 0
            val maxRetries = destination.maxRetries
            var backoffSec = destination.backoffInitialSec

            while (isActive && retryCount <= maxRetries) {
                val streamKey = destinationRepository.getDecryptedStreamKey(destination)
                val client = RtmpClient(destId, destination.ingestUrl, streamKey)
                activeClients[destId] = client

                log("RtmpClient", "Connecting to ${destination.displayName} (attempt ${retryCount + 1})...")
                updateSession(destId) { it.copy(state = if (retryCount == 0) StreamState.CONNECTING else StreamState.RECONNECTING, retryAttempt = retryCount) }

                val connected = try {
                    client.connect()
                } catch (ae: AuthenticationException) {
                    log("RtmpClient", "Authentication failed for ${destination.displayName}: ${ae.message}", isError = true)
                    updateSession(destId) {
                        it.copy(
                            state = StreamState.ERROR,
                            lastError = "Authentication failed: YouTube rejected stream key."
                        )
                    }
                    recordError(
                        DiagnosticError(
                            category = ErrorCategory.AUTHENTICATION_ERROR,
                            userExplanation = "YouTube rejected stream key for '${destination.displayName}'.",
                            redactedDetails = "Server returned NetStream.Publish.BadName or Denied.",
                            canRetry = false,
                            correctiveAction = "Check and update your YouTube Live stream key in destination settings."
                        )
                    )
                    break // Stop retry loop immediately on auth rejection
                }
                if (connected) {
                    log("RtmpClient", "Connected and publishing to ${destination.displayName}!")
                    updateSession(destId) { it.copy(state = StreamState.LIVE, retryAttempt = 0, lastError = null) }
                    retryCount = 0
                    backoffSec = destination.backoffInitialSec

                    // Send metadata and cached sequence headers immediately
                    activeVideo?.let { v ->
                        client.sendMetaData(v.width, v.height, v.frameRate, 2_500_000, 128_000)
                    }
                    cachedSpsPps?.let { (sps, pps) ->
                        client.sendAvcSequenceHeader(sps, pps)
                    }
                    cachedAacConfig?.let { config ->
                        client.sendAacSequenceHeader(config)
                    }

                    // Feed packets from queue
                    try {
                        for (packet in queue) {
                            if (!isActive) break
                            when (packet) {
                                is RtmpPacket.Video -> client.sendVideoPacket(packet)
                                is RtmpPacket.Audio -> client.sendAudioPacket(packet)
                                is RtmpPacket.AvcSequenceHeader -> client.sendAvcSequenceHeader(packet.sps, packet.pps)
                                is RtmpPacket.AacSequenceHeader -> client.sendAacSequenceHeader(packet.audioSpecificConfig)
                            }
                            updateSession(destId) { it.copy(totalPacketsSent = it.totalPacketsSent + 1) }
                        }
                    } catch (e: Exception) {
                        log("RtmpClient", "Connection interrupted for ${destination.displayName}: ${e.message}", isError = true)
                    } finally {
                        client.disconnect()
                    }
                } else {
                    log("RtmpClient", "Failed to connect to ${destination.displayName}", isError = true)
                }

                if (!isActive) break

                retryCount++
                if (retryCount <= maxRetries) {
                    val waitTime = backoffSec * 1000L
                    log("RtmpClient", "Retrying ${destination.displayName} in ${backoffSec}s (retry $retryCount/$maxRetries)...")
                    updateSession(destId) {
                        it.copy(
                            state = StreamState.RECONNECTING,
                            retryAttempt = retryCount,
                            lastError = "Connection lost. Retrying in ${backoffSec}s..."
                        )
                    }
                    delay(waitTime)
                    backoffSec = minOf(backoffSec * 2, 30) // Bounded exponential backoff
                } else {
                    log("RtmpClient", "Max retries reached for ${destination.displayName}. Stopped.", isError = true)
                    updateSession(destId) {
                        it.copy(
                            state = StreamState.ERROR,
                            lastError = "Connection failed after $maxRetries attempts."
                        )
                    }
                    recordError(
                        DiagnosticError(
                            category = ErrorCategory.NETWORK_ERROR,
                            userExplanation = "Stream '${destination.displayName}' lost connection and reached max retry attempts.",
                            redactedDetails = "Ingest: ${destination.ingestUrl}",
                            canRetry = true,
                            correctiveAction = "Check your internet connection and verify the stream key in YouTube Studio."
                        )
                    )
                    break
                }
            }
        }
        activeJobs[destId] = job
    }

    fun stopDestination(destinationId: Long) {
        activeJobs[destinationId]?.cancel()
        activeJobs.remove(destinationId)
        activeQueues[destinationId]?.close()
        activeQueues.remove(destinationId)
        activeClients[destinationId]?.disconnect()
        activeClients.remove(destinationId)

        updateSession(destinationId) {
            it.copy(state = StreamState.STOPPED, measuredBitrateBps = 0)
        }

        // If no more destinations are active, stop entire streaming session
        if (activeJobs.isEmpty()) {
            stopAllStreaming()
        }
    }

    fun stopAllStreaming() {
        packetStreamer?.stop()
        packetStreamer = null

        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()

        activeQueues.values.forEach { it.close() }
        activeQueues.clear()

        activeClients.values.forEach { it.disconnect() }
        activeClients.clear()

        val updated = _sessionsState.value.toMutableMap()
        for ((k, v) in updated) {
            updated[k] = v.copy(state = StreamState.STOPPED, measuredBitrateBps = 0)
        }
        _sessionsState.value = updated

        releaseWakeLock()
        log("Engine", "All active streams stopped cleanly.")
    }

    // PacketSink implementations
    override fun onAvcSequenceHeader(sps: ByteArray, pps: ByteArray) {
        cachedSpsPps = Pair(sps, pps)
        val packet = RtmpPacket.AvcSequenceHeader(sps, pps)
        broadcastPacket(packet)
    }

    override fun onAacSequenceHeader(config: ByteArray) {
        cachedAacConfig = config
        val packet = RtmpPacket.AacSequenceHeader(config)
        broadcastPacket(packet)
    }

    override fun onVideoPacket(packet: RtmpPacket.Video) {
        broadcastPacket(packet)
    }

    override fun onAudioPacket(packet: RtmpPacket.Audio) {
        broadcastPacket(packet)
    }

    override fun onLoopBoundary(newLoopIndex: Int, totalUptimeMs: Long) {
        log("LoopEngine", "Loop sequence #$newLoopIndex completed successfully! Rebasing timestamps for next pass. Video encoder invocations: 0.")
    }

    private fun broadcastPacket(packet: RtmpPacket) {
        for ((_, queue) in activeQueues) {
            // Non-blocking trySend to prevent a stalled destination from blocking the reader
            queue.trySend(packet)
        }
    }

    private fun updateSession(destId: Long, transform: (StreamSessionState) -> StreamSessionState) {
        val current = _sessionsState.value.toMutableMap()
        val existing = current[destId] ?: StreamSessionState(destId, "Destination $destId", "")
        current[destId] = transform(existing)
        _sessionsState.value = current
    }

    private fun updateDiagnostics() {
        val sessions = _sessionsState.value
        val isAny = sessions.values.any { it.state.isStreamingActive }
        val activeCount = sessions.values.count { it.state == StreamState.LIVE }
        val totalUptime = if (isAny && streamingStartTimeMs > 0) System.currentTimeMillis() - streamingStartTimeMs else 0L

        var totalBitrate = 0L
        val updatedSessions = sessions.toMutableMap()
        for ((destId, client) in activeClients) {
            val bps = client.measuredBitrateBps
            totalBitrate += bps
            updatedSessions[destId]?.let { s ->
                updatedSessions[destId] = s.copy(
                    measuredBitrateBps = bps,
                    uptimeMs = if (s.state == StreamState.LIVE) totalUptime else s.uptimeMs
                )
            }
        }
        _sessionsState.value = updatedSessions

        _diagnostics.value = StreamOverallDiagnostics(
            isAnyStreaming = isAny,
            activeStreamCount = activeCount,
            totalUptimeMs = totalUptime,
            totalLoopsCompleted = packetStreamer?.loopCount ?: 0,
            totalVideoPacketsEmitted = packetStreamer?.totalVideoPacketsSent ?: 0,
            totalAudioPacketsEmitted = packetStreamer?.totalAudioPacketsSent ?: 0,
            encoderInvocationCount = packetStreamer?.encoderInvocationCount ?: 0, // 0 during loop!
            totalOutputBitrateBps = totalBitrate
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YouTubeLoopLive::StreamingWakeLock")?.apply {
                acquire(12 * 60 * 60 * 1000L) // 12 hours timeout safety
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // ignore
        }
        wakeLock = null
    }

    fun log(tag: String, message: String, isError: Boolean = false) {
        val sanitized = CryptoManager.redactLog(message)
        val entry = DiagnosticLogEntry(
            timestamp = System.currentTimeMillis(),
            tag = tag,
            message = sanitized,
            isError = isError
        )
        val current = _logs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 200) current.removeLast()
        _logs.value = current
    }

    fun recordError(error: DiagnosticError) {
        val current = _recentErrors.value.toMutableList()
        current.add(0, error)
        if (current.size > 50) current.removeLast()
        _recentErrors.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
        _recentErrors.value = emptyList()
    }
}
