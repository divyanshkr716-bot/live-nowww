package com.example.media.preparation

import com.example.data.model.PreparationPreset
import java.security.MessageDigest

object PreparationFingerprint {

    fun generateSourceFingerprint(displayName: String, fileSize: Long, durationMs: Long): String {
        val raw = "$displayName|$fileSize|$durationMs"
        return sha256(raw)
    }

    fun generateSettingsFingerprint(preset: PreparationPreset): String {
        val raw = "${preset.name}|${preset.targetWidth}|${preset.targetHeight}|${preset.targetFps}|${preset.targetVideoBitrateBps}|${preset.targetKeyframeIntervalSec}"
        return sha256(raw)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
