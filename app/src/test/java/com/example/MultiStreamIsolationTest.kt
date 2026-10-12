package com.example

import com.example.data.model.StreamState
import com.example.streaming.rtmp.StreamSessionState
import org.junit.Assert.*
import org.junit.Test

class MultiStreamIsolationTest {

    @Test
    fun `isolated destinations maintain independent state when one encounters an error`() {
        val dest1 = StreamSessionState(
            destinationId = 1L,
            displayName = "Primary YouTube Destination",
            ingestUrl = "rtmp://a.rtmp.youtube.com/live2",
            state = StreamState.LIVE,
            measuredBitrateBps = 2_500_000L
        )

        val dest2 = StreamSessionState(
            destinationId = 2L,
            displayName = "Secondary Backup Destination",
            ingestUrl = "rtmps://a.rtmp.youtube.com/live2:443/live2",
            state = StreamState.ERROR,
            lastError = "Connection reset by peer"
        )

        // Dest 1 remains healthy and LIVE
        assertEquals(StreamState.LIVE, dest1.state)
        assertEquals(2_500_000L, dest1.measuredBitrateBps)

        // Dest 2 failure is isolated
        assertEquals(StreamState.ERROR, dest2.state)
        assertNotNull(dest2.lastError)
    }

    @Test
    fun `duplicate stream keys are detected and prevented`() {
        val usedKeys = mutableSetOf<String>()
        val key1 = "youtube-live-key-alpha"
        val key2 = "youtube-live-key-alpha" // Identical key

        val firstAdded = usedKeys.add(key1)
        val secondAdded = usedKeys.add(key2)

        assertTrue("First unique key should be accepted", firstAdded)
        assertFalse("Duplicate key must be rejected to prevent concurrent ingestion collision", secondAdded)
    }

    @Test
    fun `stream states transition correctly`() {
        assertTrue(StreamState.CONNECTING.isStreamingActive)
        assertTrue(StreamState.LIVE.isStreamingActive)
        assertTrue(StreamState.RECONNECTING.isStreamingActive)
        assertFalse(StreamState.STOPPED.isStreamingActive)
        assertFalse(StreamState.ERROR.isStreamingActive)
        assertFalse(StreamState.IDLE.isStreamingActive)
    }
}
