package com.example.streaming.rtmp

import android.content.Context
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.rtmp.RtmpFromFile
import java.io.File

/**
 * Setup and adapter example for a single stream session using RootEncoder
 * (com.github.pedroSG94.RootEncoder:library, the modern release of rtmp-rtsp-stream-client-java).
 *
 * Demonstrates:
 * 1. Initializing the RTMP client with connection callbacks (ConnectChecker).
 * 2. Connecting to YouTube Live (RTMP/RTMPS) with auth and error reporting.
 * 3. Streaming a prepared video file in an infinite loop directly on the phone.
 * 4. Safe resource release on disconnect.
 */
class RootEncoderSession(
    private val context: Context,
    private val onStateChanged: (isConnected: Boolean, error: String?) -> Unit
) : ConnectChecker, VideoDecoderInterface, AudioDecoderInterface {

    private var rtmpStream: RtmpFromFile? = null
    var isStreaming: Boolean = false
        private set

    /**
     * Initializes and starts a single RTMP/RTMPS live streaming session for a prepared video.
     *
     * @param preparedVideoFile The local validated prepared MP4 file.
     * @param ingestUrl Base YouTube Live ingest URL (e.g. "rtmp://a.rtmp.youtube.com/live2").
     * @param streamKey The protected YouTube stream key.
     * @param loopVideo Whether to loop the file repeatedly.
     */
    fun startSession(
        preparedVideoFile: File,
        ingestUrl: String,
        streamKey: String,
        loopVideo: Boolean = true
    ): Boolean {
        if (isStreaming) {
            stopSession()
        }

        // Initialize RtmpFromFile passing this for ConnectChecker, VideoDecoderInterface, and AudioDecoderInterface
        val stream = RtmpFromFile(this, this, this)
        rtmpStream = stream

        val streamEndpoint = if (ingestUrl.endsWith("/")) {
            "$ingestUrl$streamKey"
        } else {
            "$ingestUrl/$streamKey"
        }

        // Prepare stream from local video file
        val prepared = stream.prepareVideo(preparedVideoFile.absolutePath)
        val audioPrepared = stream.prepareAudio(preparedVideoFile.absolutePath)

        if (!prepared) {
            onStateChanged(false, "Failed to prepare video stream from ${preparedVideoFile.name}")
            return false
        }

        // Configure loop mode on video stream
        stream.setLoopMode(loopVideo)

        // Connect and publish to YouTube Live
        stream.startStream(streamEndpoint)
        isStreaming = true
        return true
    }

    /**
     * Stops the active RTMP stream and releases network & decoder resources.
     */
    fun stopSession() {
        try {
            rtmpStream?.stopStream()
            rtmpStream = null
        } catch (e: Exception) {
            // ignore
        } finally {
            isStreaming = false
        }
    }

    // --- ConnectChecker Callbacks ---

    override fun onConnectionStarted(url: String) {
        onStateChanged(false, null)
    }

    override fun onConnectionSuccess() {
        isStreaming = true
        onStateChanged(true, null)
    }

    override fun onConnectionFailed(reason: String) {
        isStreaming = false
        onStateChanged(false, "Connection failed: $reason")
    }

    override fun onNewBitrate(bitrate: Long) {
        // Bitrate update event (in bps)
    }

    override fun onDisconnect() {
        isStreaming = false
        onStateChanged(false, null)
    }

    override fun onAuthError() {
        isStreaming = false
        onStateChanged(false, "Authentication error: Invalid YouTube stream key.")
    }

    override fun onAuthSuccess() {
        // Auth passed
    }

    // --- VideoDecoderInterface Callbacks ---

    override fun onVideoDecoderFinished() {
        // Callback when video file reaches EOF in non-loop mode
    }

    // --- AudioDecoderInterface Callbacks ---

    override fun onAudioDecoderFinished() {
        // Callback when audio track reaches EOF in non-loop mode
    }
}
