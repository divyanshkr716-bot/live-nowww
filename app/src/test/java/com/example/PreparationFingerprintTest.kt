package com.example

import com.example.data.model.PreparationPreset
import com.example.media.preparation.PreparationFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PreparationFingerprintTest {

    @Test
    fun `source fingerprint is deterministic for identical media`() {
        val fp1 = PreparationFingerprint.generateSourceFingerprint("video.mp4", 1024000L, 60000L)
        val fp2 = PreparationFingerprint.generateSourceFingerprint("video.mp4", 1024000L, 60000L)

        assertEquals("Fingerprints must match for identical media metadata", fp1, fp2)
    }

    @Test
    fun `source fingerprint invalidates when file size or duration changes`() {
        val original = PreparationFingerprint.generateSourceFingerprint("video.mp4", 1024000L, 60000L)
        val modifiedSize = PreparationFingerprint.generateSourceFingerprint("video.mp4", 1024500L, 60000L)
        val modifiedDuration = PreparationFingerprint.generateSourceFingerprint("video.mp4", 1024000L, 61000L)

        assertNotEquals("Size change must invalidate source fingerprint", original, modifiedSize)
        assertNotEquals("Duration change must invalidate source fingerprint", original, modifiedDuration)
    }

    @Test
    fun `settings fingerprint distinguishes between different preparation presets`() {
        val remuxFp = PreparationFingerprint.generateSettingsFingerprint(PreparationPreset.PASSTHROUGH_REMUX)
        val p1080Fp = PreparationFingerprint.generateSettingsFingerprint(PreparationPreset.YOUTUBE_1080P)
        val p720Fp = PreparationFingerprint.generateSettingsFingerprint(PreparationPreset.YOUTUBE_720P)
        val p480Fp = PreparationFingerprint.generateSettingsFingerprint(PreparationPreset.YOUTUBE_480P)

        assertNotEquals(remuxFp, p1080Fp)
        assertNotEquals(p1080Fp, p720Fp)
        assertNotEquals(p720Fp, p480Fp)
    }
}
