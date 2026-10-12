package com.example

import com.example.streaming.rtmp.TimestampRebaser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampRebaserTest {

    @Test
    fun `rebase keeps timestamps monotonically increasing within a loop`() {
        val rebaser = TimestampRebaser()
        val t1 = rebaser.rebaseTimestamp(0L)
        val t2 = rebaser.rebaseTimestamp(33_333L)
        val t3 = rebaser.rebaseTimestamp(66_666L)

        assertTrue("t2 should be greater than t1", t2 > t1)
        assertTrue("t3 should be greater than t2", t3 > t2)
    }

    @Test
    fun `rebase maintains strict monotonic progression across loop boundary restart`() {
        val rebaser = TimestampRebaser(nominalFrameIntervalUs = 33_333L)

        // Loop 1
        val loop1_start = rebaser.rebaseTimestamp(0L)
        val loop1_mid = rebaser.rebaseTimestamp(500_000L)
        val loop1_end = rebaser.rebaseTimestamp(1_000_000L)

        // Loop boundary occurs
        rebaser.onLoopRestart()

        // Loop 2 starts with raw sample time reset to 0
        val loop2_start = rebaser.rebaseTimestamp(0L)
        val loop2_mid = rebaser.rebaseTimestamp(500_000L)
        val loop2_end = rebaser.rebaseTimestamp(1_000_000L)

        assertTrue(
            "Loop 2 start ($loop2_start) must be strictly greater than Loop 1 end ($loop1_end)",
            loop2_start > loop1_end
        )
        assertTrue(
            "Loop 2 mid ($loop2_mid) must be strictly greater than Loop 2 start ($loop2_start)",
            loop2_mid > loop2_start
        )
        assertTrue(
            "Loop 2 end ($loop2_end) must be strictly greater than Loop 2 mid ($loop2_mid)",
            loop2_end > loop2_mid
        )
    }

    @Test
    fun `rebase enforces progression even if raw timestamps stutter or move backwards`() {
        val rebaser = TimestampRebaser()
        val t1 = rebaser.rebaseTimestamp(100_000L)
        val t2 = rebaser.rebaseTimestamp(80_000L) // Erroneous backwards timestamp in source file

        assertTrue("Timestamp must never decrease even on source jitter", t2 > t1)
    }
}
