package com.example

import com.example.data.security.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoSecurityTest {

    @Test
    fun `maskStreamKey masks all except last 4 characters`() {
        val plainKey = "abcd-1234-efgh-9876"
        val masked = CryptoManager.maskStreamKey(plainKey)

        assertTrue("Masked key must end with last 4 chars", masked.endsWith("9876"))
        assertFalse("Masked key must not contain earlier characters", masked.contains("abcd"))
    }

    @Test
    fun `redactLog removes stream keys embedded in RTMP URLs`() {
        val rawLog = "Connecting to rtmp://a.rtmp.youtube.com/live2/abcd-efgh-1234-5678 with timeout 8000ms"
        val sanitized = CryptoManager.redactLog(rawLog)

        assertFalse("Sanitized log must not expose stream key", sanitized.contains("abcd-efgh-1234-5678"))
        assertTrue("Sanitized log should show redacted token", sanitized.contains("[REDACTED_STREAM_KEY]"))
    }

    @Test
    fun `redactLog removes query parameter secrets`() {
        val rawLog = "Stream rejected by endpoint: key=secret_token_12345678"
        val sanitized = CryptoManager.redactLog(rawLog)

        assertFalse("Sanitized log must not expose query key", sanitized.contains("secret_token_12345678"))
        assertTrue("Sanitized log should show redacted placeholder", sanitized.contains("[REDACTED]"))
    }
}
