package com.example.streaming.rtmp

import android.util.Log
import com.example.data.security.CryptoManager
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class AuthenticationException(message: String) : IOException(message)

class RtmpClient(
    val destinationId: Long,
    val ingestUrl: String,
    private val streamKey: String
) {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private var clientChunkSize = 4096
    private var serverChunkSize = 128
    private var streamId = 1
    var isConnected = false
        private set
    var isPublishing = false
        private set

    // Bitrate measurement
    private var totalBytesSent = 0L
    private var lastBitrateMeasureTime = System.currentTimeMillis()
    private var bytesSinceLastMeasure = 0L
    var measuredBitrateBps: Long = 0
        private set

    // Incomplete incoming chunk messages by chunk stream ID
    private val incomingMessages = ConcurrentHashMap<Int, IncompleteMessage>()

    private var readerJob: Job? = null
    private var publishConfirmationCompleter: CompletableDeferred<Boolean>? = null
    private var createStreamCompleter: CompletableDeferred<Int>? = null

    private data class IncompleteMessage(
        var messageType: Int = 0,
        var messageLength: Int = 0,
        var timestamp: Long = 0,
        var streamId: Int = 0,
        var buffer: ByteArrayOutputStream = ByteArrayOutputStream()
    )

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val (isSsl, host, port, appName) = parseIngestUrl(ingestUrl)

            val rawSocket = if (isSsl) {
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = sslFactory.createSocket() as SSLSocket
                // Support SNI and strict TLS certificate verification
                val sslParams = sslSocket.sslParameters
                try {
                    sslParams.serverNames = listOf(SNIHostName(host))
                    sslSocket.sslParameters = sslParams
                } catch (e: Exception) {
                    // SNI fallback
                }
                sslSocket
            } else {
                Socket()
            }

            rawSocket.tcpNoDelay = true
            rawSocket.sendBufferSize = 1024 * 256
            rawSocket.receiveBufferSize = 1024 * 128
            rawSocket.connect(InetSocketAddress(host, port), 9000)

            socket = rawSocket
            outputStream = BufferedOutputStream(rawSocket.getOutputStream(), 65536)
            inputStream = BufferedInputStream(rawSocket.getInputStream(), 65536)

            // Step 1: Handshake (C0/C1 -> S0/S1/S2 -> C2)
            performHandshake()

            // Step 2: Set client chunk size
            sendSetChunkSize(clientChunkSize)

            // Step 3: Send Connect command
            sendConnectCommand(appName, isSsl, host, port)

            // Step 4: ReleaseStream & FCPublish
            sendReleaseStream(streamKey)
            sendFCPublish(streamKey)

            // Step 5: CreateStream and await actual assigned stream ID
            createStreamCompleter = CompletableDeferred()
            publishConfirmationCompleter = CompletableDeferred()

            // Launch background incoming chunk reader
            val input = inputStream ?: throw IOException("Input stream is null")
            readerJob = launch(Dispatchers.IO) {
                runChunkReader(input)
            }

            sendCreateStream()

            // Wait for createStream response with timeout
            val assignedStreamId = withTimeoutOrNull(8000) {
                createStreamCompleter?.await()
            } ?: 1
            streamId = assignedStreamId

            // Step 6: Publish stream
            sendPublish(streamKey)

            // Wait for publish status confirmation ("NetStream.Publish.Start")
            val confirmed = withTimeoutOrNull(9000) {
                publishConfirmationCompleter?.await()
            } ?: false

            if (!confirmed) {
                throw IOException("Server did not confirm publication start within timeout.")
            }

            isConnected = true
            isPublishing = true
            true
        } catch (ae: AuthenticationException) {
            val sanitized = CryptoManager.redactLog(ae.message ?: "Authentication error")
            Log.e("RtmpClient", "Authentication failed for dest $destinationId: $sanitized")
            disconnect()
            throw ae
        } catch (e: Exception) {
            val sanitizedMsg = CryptoManager.redactLog(e.localizedMessage ?: "Unknown RTMP connect error")
            Log.e("RtmpClient", "Connect failed for dest $destinationId: $sanitizedMsg")
            disconnect()
            false
        }
    }

    private fun performHandshake() {
        val out = outputStream ?: throw IOException("Output stream null")
        val input = inputStream ?: throw IOException("Input stream null")

        // C0 (1 byte: version 3) + C1 (1536 bytes)
        val c1 = ByteArray(1536)
        Random().nextBytes(c1)
        c1[0] = 0; c1[1] = 0; c1[2] = 0; c1[3] = 0
        c1[4] = 0; c1[5] = 0; c1[6] = 0; c1[7] = 0

        out.write(0x03)
        out.write(c1)
        out.flush()

        // Read S0 (1 byte)
        val s0 = input.read()
        if (s0 != 0x03) {
            throw IOException("RTMP handshake failed: S0 version $s0 is not 3")
        }

        // Read S1 (1536 bytes)
        val s1 = ByteArray(1536)
        readFully(input, s1)

        // Write C2 (1536 bytes echoing S1)
        out.write(s1)
        out.flush()

        // Read S2 (1536 bytes)
        val s2 = ByteArray(1536)
        readFully(input, s2)
    }

    private fun runChunkReader(input: InputStream) {
        val dis = DataInputStream(input)
        try {
            while (true) {
                val headerByte = dis.read()
                if (headerByte == -1) break

                val fmt = (headerByte shr 6) and 0x03
                var csid = headerByte and 0x3F

                if (csid == 0) {
                    csid = 64 + dis.readUnsignedByte()
                } else if (csid == 1) {
                    val b1 = dis.readUnsignedByte()
                    val b2 = dis.readUnsignedByte()
                    csid = 64 + b1 + (b2 shl 8)
                }

                val msg = incomingMessages.getOrPut(csid) { IncompleteMessage() }

                when (fmt) {
                    0 -> {
                        // 11 bytes: timestamp (3), length (3), type (1), streamId (4 little endian)
                        val ts = read24BitInt(dis)
                        val length = read24BitInt(dis)
                        val type = dis.readUnsignedByte()
                        val sid = read32BitLittleEndian(dis)
                        msg.timestamp = ts.toLong()
                        msg.messageLength = length
                        msg.messageType = type
                        msg.streamId = sid
                        msg.buffer = ByteArrayOutputStream(length)
                    }
                    1 -> {
                        // 7 bytes: timestamp delta (3), length (3), type (1)
                        val delta = read24BitInt(dis)
                        val length = read24BitInt(dis)
                        val type = dis.readUnsignedByte()
                        msg.timestamp += delta
                        msg.messageLength = length
                        msg.messageType = type
                        msg.buffer = ByteArrayOutputStream(length)
                    }
                    2 -> {
                        // 3 bytes: timestamp delta (3)
                        val delta = read24BitInt(dis)
                        msg.timestamp += delta
                    }
                    3 -> {
                        // Continuous chunk
                    }
                }

                val remainingInMessage = msg.messageLength - msg.buffer.size()
                val bytesToReadThisChunk = minOf(serverChunkSize, remainingInMessage)
                if (bytesToReadThisChunk > 0) {
                    val chunkData = ByteArray(bytesToReadThisChunk)
                    dis.readFully(chunkData)
                    msg.buffer.write(chunkData)
                }

                if (msg.buffer.size() >= msg.messageLength) {
                    // Full RTMP message assembled
                    handleCompleteMessage(msg.messageType, msg.buffer.toByteArray())
                    msg.buffer.reset()
                }
            }
        } catch (e: Exception) {
            // Socket closed or EOF
        }
    }

    private fun handleCompleteMessage(messageType: Int, payload: ByteArray) {
        when (messageType) {
            0x01 -> { // Set Chunk Size
                if (payload.size >= 4) {
                    val newSize = ((payload[0].toInt() and 0xFF) shl 24) or
                            ((payload[1].toInt() and 0xFF) shl 16) or
                            ((payload[2].toInt() and 0xFF) shl 8) or
                            (payload[3].toInt() and 0xFF)
                    if (newSize in 1..65536) {
                        serverChunkSize = newSize
                    }
                }
            }
            0x14 -> { // AMF0 Command
                handleAmf0Command(payload)
            }
        }
    }

    private fun handleAmf0Command(payload: ByteArray) {
        val values = try {
            Amf0.readAll(payload)
        } catch (e: Exception) {
            return
        }

        if (values.isEmpty()) return
        val commandName = values[0] as? String ?: return

        when (commandName) {
            "_result" -> {
                val transactionId = (values.getOrNull(1) as? Number)?.toDouble() ?: 0.0
                if (transactionId == 4.0) { // createStream transaction ID
                    val sid = (values.getOrNull(3) as? Number)?.toInt() ?: 1
                    createStreamCompleter?.complete(sid)
                }
            }
            "onStatus" -> {
                val info = values.getOrNull(3) as? Map<*, *>
                val code = info?.get("code") as? String ?: ""
                val level = info?.get("level") as? String ?: ""
                val desc = info?.get("description") as? String ?: ""

                if (code == "NetStream.Publish.Start") {
                    publishConfirmationCompleter?.complete(true)
                } else if (code == "NetStream.Publish.BadName" || code == "NetStream.Publish.Denied") {
                    val err = AuthenticationException("YouTube rejected stream key ($code): $desc")
                    publishConfirmationCompleter?.completeExceptionally(err)
                } else if (level == "error") {
                    val err = IOException("Publish error ($code): $desc")
                    publishConfirmationCompleter?.completeExceptionally(err)
                }
            }
            "_error" -> {
                val info = values.getOrNull(3) as? Map<*, *>
                val desc = info?.get("description") as? String ?: "Command rejected by RTMP server"
                val err = IOException("RTMP Server Error: $desc")
                createStreamCompleter?.completeExceptionally(err)
                publishConfirmationCompleter?.completeExceptionally(err)
            }
        }
    }

    private fun sendSetChunkSize(size: Int) {
        val out = outputStream ?: return
        val chunk = ByteArrayOutputStream()
        chunk.write(0x02)
        chunk.write(0); chunk.write(0); chunk.write(0)
        chunk.write(0); chunk.write(0); chunk.write(4)
        chunk.write(0x01) // Message Type 0x01
        chunk.write(0); chunk.write(0); chunk.write(0); chunk.write(0)

        chunk.write((size shr 24) and 0xFF)
        chunk.write((size shr 16) and 0xFF)
        chunk.write((size shr 8) and 0xFF)
        chunk.write(size and 0xFF)

        val bytes = chunk.toByteArray()
        out.write(bytes)
        out.flush()
        trackSentBytes(bytes.size.toLong())
    }

    private fun sendConnectCommand(appName: String, isSsl: Boolean, host: String, port: Int) {
        val payload = ByteArrayOutputStream()
        Amf0.writeString(payload, "connect")
        Amf0.writeNumber(payload, 1.0)
        val tcUrl = "${if (isSsl) "rtmps" else "rtmp"}://$host:$port/$appName"
        val params = mapOf(
            "app" to appName,
            "flashVer" to "FMLE/3.0 (compatible; Android Loop)",
            "tcUrl" to tcUrl,
            "fpad" to false,
            "capabilities" to 15.0,
            "audioCodecs" to 3191.0,
            "videoCodecs" to 252.0,
            "videoFunction" to 1.0
        )
        Amf0.writeObject(payload, params)

        sendRtmpMessage(chunkStreamId = 3, messageType = 0x14, streamId = 0, timestampMs = 0, payload = payload.toByteArray())
    }

    private fun sendReleaseStream(key: String) {
        val payload = ByteArrayOutputStream()
        Amf0.writeString(payload, "releaseStream")
        Amf0.writeNumber(payload, 2.0)
        Amf0.writeNull(payload)
        Amf0.writeString(payload, key)
        sendRtmpMessage(chunkStreamId = 3, messageType = 0x14, streamId = 0, timestampMs = 0, payload = payload.toByteArray())
    }

    private fun sendFCPublish(key: String) {
        val payload = ByteArrayOutputStream()
        Amf0.writeString(payload, "FCPublish")
        Amf0.writeNumber(payload, 3.0)
        Amf0.writeNull(payload)
        Amf0.writeString(payload, key)
        sendRtmpMessage(chunkStreamId = 3, messageType = 0x14, streamId = 0, timestampMs = 0, payload = payload.toByteArray())
    }

    private fun sendCreateStream() {
        val payload = ByteArrayOutputStream()
        Amf0.writeString(payload, "createStream")
        Amf0.writeNumber(payload, 4.0)
        Amf0.writeNull(payload)
        sendRtmpMessage(chunkStreamId = 3, messageType = 0x14, streamId = 0, timestampMs = 0, payload = payload.toByteArray())
    }

    private fun sendPublish(key: String) {
        val payload = ByteArrayOutputStream()
        Amf0.writeString(payload, "publish")
        Amf0.writeNumber(payload, 5.0)
        Amf0.writeNull(payload)
        Amf0.writeString(payload, key)
        Amf0.writeString(payload, "live")
        sendRtmpMessage(chunkStreamId = 3, messageType = 0x14, streamId = streamId, timestampMs = 0, payload = payload.toByteArray())
    }

    fun sendMetaData(width: Int, height: Int, fps: Double, videoBitrate: Long, audioBitrate: Long) {
        val payload = ByteArrayOutputStream()
        Amf0.writeString(payload, "@setDataFrame")
        Amf0.writeString(payload, "onMetaData")
        val meta = mutableMapOf<String, Any?>(
            "width" to width.toDouble(),
            "height" to height.toDouble(),
            "framerate" to fps,
            "videocodecid" to 7.0, // AVC
            "audiocodecid" to 10.0, // AAC
            "duration" to 0.0
        )
        if (videoBitrate > 0) meta["videodatarate"] = (videoBitrate / 1000.0)
        if (audioBitrate > 0) meta["audiodatarate"] = (audioBitrate / 1000.0)
        Amf0.writeEcmaArray(payload, meta)

        sendRtmpMessage(chunkStreamId = 3, messageType = 0x12, streamId = streamId, timestampMs = 0, payload = payload.toByteArray())
    }

    fun sendAvcSequenceHeader(sps: ByteArray, pps: ByteArray) {
        val payload = ByteArrayOutputStream()
        payload.write(0x17) // Keyframe + AVC
        payload.write(0x00) // AVC sequence header
        payload.write(0); payload.write(0); payload.write(0)

        // AVCDecoderConfigurationRecord
        payload.write(0x01)
        payload.write(sps[1].toInt() and 0xFF)
        payload.write(sps[2].toInt() and 0xFF)
        payload.write(sps[3].toInt() and 0xFF)
        payload.write(0xFF)
        payload.write(0xE1)
        payload.write((sps.size shr 8) and 0xFF)
        payload.write(sps.size and 0xFF)
        payload.write(sps)
        payload.write(0x01)
        payload.write((pps.size shr 8) and 0xFF)
        payload.write(pps.size and 0xFF)
        payload.write(pps)

        sendRtmpMessage(chunkStreamId = 4, messageType = 0x09, streamId = streamId, timestampMs = 0, payload = payload.toByteArray())
    }

    fun sendAacSequenceHeader(config: ByteArray) {
        val payload = ByteArrayOutputStream()
        payload.write(0xAF) // AAC LC
        payload.write(0x00) // Sequence header
        payload.write(config)

        sendRtmpMessage(chunkStreamId = 5, messageType = 0x08, streamId = streamId, timestampMs = 0, payload = payload.toByteArray())
    }

    fun sendVideoPacket(packet: RtmpPacket.Video) {
        if (!isPublishing) return
        val payload = ByteArrayOutputStream(packet.naluData.size + 9)
        val frameHeader = if (packet.isKeyframe) 0x17 else 0x27
        payload.write(frameHeader)
        payload.write(0x01) // AVC NALU
        val comp = packet.compositionTimeOffsetMs
        payload.write(((comp shr 16) and 0xFF).toInt())
        payload.write(((comp shr 8) and 0xFF).toInt())
        payload.write((comp and 0xFF).toInt())

        payload.write(packet.naluData)

        sendRtmpMessage(chunkStreamId = 4, messageType = 0x09, streamId = streamId, timestampMs = packet.timestampMs, payload = payload.toByteArray())
    }

    fun sendAudioPacket(packet: RtmpPacket.Audio) {
        if (!isPublishing) return
        val payload = ByteArrayOutputStream(packet.aacData.size + 2)
        payload.write(0xAF)
        payload.write(0x01) // AAC raw
        payload.write(packet.aacData)

        sendRtmpMessage(chunkStreamId = 5, messageType = 0x08, streamId = streamId, timestampMs = packet.timestampMs, payload = payload.toByteArray())
    }

    private fun sendRtmpMessage(
        chunkStreamId: Int,
        messageType: Int,
        streamId: Int,
        timestampMs: Long,
        payload: ByteArray
    ) {
        val out = outputStream ?: return
        val payloadLength = payload.size
        var offset = 0
        val isExtendedTimestamp = timestampMs >= 0xFFFFFFL
        val headerTs = if (isExtendedTimestamp) 0xFFFFFFL else timestampMs

        out.write(chunkStreamId and 0x3F)
        out.write(((headerTs shr 16) and 0xFF).toInt())
        out.write(((headerTs shr 8) and 0xFF).toInt())
        out.write((headerTs and 0xFF).toInt())
        out.write(((payloadLength shr 16) and 0xFF))
        out.write(((payloadLength shr 8) and 0xFF))
        out.write((payloadLength and 0xFF))
        out.write(messageType)
        out.write(streamId and 0xFF)
        out.write((streamId shr 8) and 0xFF)
        out.write((streamId shr 16) and 0xFF)
        out.write((streamId shr 24) and 0xFF)

        if (isExtendedTimestamp) {
            out.write(((timestampMs shr 24) and 0xFF).toInt())
            out.write(((timestampMs shr 16) and 0xFF).toInt())
            out.write(((timestampMs shr 8) and 0xFF).toInt())
            out.write((timestampMs and 0xFF).toInt())
        }

        var bytesWritten = 12 + if (isExtendedTimestamp) 4 else 0

        val firstChunkSize = minOf(clientChunkSize, payloadLength)
        out.write(payload, 0, firstChunkSize)
        offset += firstChunkSize
        bytesWritten += firstChunkSize

        val subsequentHeader = (0xC0 or (chunkStreamId and 0x3F)).toByte()
        while (offset < payloadLength) {
            out.write(subsequentHeader.toInt())
            bytesWritten += 1
            if (isExtendedTimestamp) {
                out.write(((timestampMs shr 24) and 0xFF).toInt())
                out.write(((timestampMs shr 16) and 0xFF).toInt())
                out.write(((timestampMs shr 8) and 0xFF).toInt())
                out.write((timestampMs and 0xFF).toInt())
                bytesWritten += 4
            }
            val thisChunkSize = minOf(clientChunkSize, payloadLength - offset)
            out.write(payload, offset, thisChunkSize)
            offset += thisChunkSize
            bytesWritten += thisChunkSize
        }

        out.flush()
        trackSentBytes(bytesWritten.toLong())
    }

    private fun trackSentBytes(bytes: Long) {
        totalBytesSent += bytes
        bytesSinceLastMeasure += bytes
        val now = System.currentTimeMillis()
        val elapsed = now - lastBitrateMeasureTime
        if (elapsed >= 1000) {
            measuredBitrateBps = (bytesSinceLastMeasure * 8 * 1000) / elapsed
            bytesSinceLastMeasure = 0
            lastBitrateMeasureTime = now
        }
    }

    fun disconnect() {
        isConnected = false
        isPublishing = false
        readerJob?.cancel()
        readerJob = null
        try { outputStream?.close() } catch (e: Exception) {}
        try { inputStream?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
        outputStream = null
        inputStream = null
        socket = null
        measuredBitrateBps = 0
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var readTotal = 0
        while (readTotal < buffer.size) {
            val r = input.read(buffer, readTotal, buffer.size - readTotal)
            if (r < 0) throw EOFException("Unexpected end of stream during RTMP read")
            readTotal += r
        }
    }

    private fun read24BitInt(dis: DataInputStream): Int {
        val b1 = dis.readUnsignedByte()
        val b2 = dis.readUnsignedByte()
        val b3 = dis.readUnsignedByte()
        return (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun read32BitLittleEndian(dis: DataInputStream): Int {
        val b1 = dis.readUnsignedByte()
        val b2 = dis.readUnsignedByte()
        val b3 = dis.readUnsignedByte()
        val b4 = dis.readUnsignedByte()
        return b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
    }

    private data class ParsedUrl(
        val isSsl: Boolean,
        val host: String,
        val port: Int,
        val appName: String
    )

    private fun parseIngestUrl(url: String): ParsedUrl {
        val trimmed = url.trim()
        val isSsl = trimmed.startsWith("rtmps://", ignoreCase = true)
        val clean = trimmed.replace("rtmp://", "").replace("rtmps://", "")

        val slashIndex = clean.indexOf('/')
        val hostPort = if (slashIndex != -1) clean.substring(0, slashIndex) else clean
        val path = if (slashIndex != -1) clean.substring(slashIndex + 1) else "live2"

        val colonIndex = hostPort.indexOf(':')
        val host = if (colonIndex != -1) hostPort.substring(0, colonIndex) else hostPort
        val defaultPort = if (isSsl) 443 else 1935
        val port = if (colonIndex != -1) {
            hostPort.substring(colonIndex + 1).toIntOrNull() ?: defaultPort
        } else {
            defaultPort
        }

        val appName = if (path.isEmpty()) "live2" else path
        return ParsedUrl(isSsl, host, port, appName)
    }
}
