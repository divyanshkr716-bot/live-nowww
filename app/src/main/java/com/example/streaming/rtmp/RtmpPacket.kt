package com.example.streaming.rtmp

sealed class RtmpPacket {
    data class Video(
        val naluData: ByteArray,
        val isKeyframe: Boolean,
        val timestampMs: Long,
        val compositionTimeOffsetMs: Long = 0
    ) : RtmpPacket()

    data class Audio(
        val aacData: ByteArray,
        val timestampMs: Long
    ) : RtmpPacket()

    data class AvcSequenceHeader(
        val sps: ByteArray,
        val pps: ByteArray
    ) : RtmpPacket()

    data class AacSequenceHeader(
        val audioSpecificConfig: ByteArray
    ) : RtmpPacket()
}
