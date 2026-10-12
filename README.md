# YouTube Loop Live

A native Android application (Kotlin & Jetpack Compose) for phone-direct, battery-optimized infinite loop live streaming to YouTube Live.

---

## 1. Project Overview

YouTube Loop Live allows users to import local videos, inspect them, prepare a compatible version once, and stream it continuously in an infinite loop without repeatedly re-encoding video frames on every loop pass.

### Key Architectural Highlights
- **Zero Video Re-Encoding during Loops:** Uses Android `MediaExtractor` to read encoded H.264 (AVC) and AAC sample packets directly, bypassing GPU/CPU video encoding on loop boundaries. Confirmed with an active encoder invocation counter of `0`.
- **Monotonic Timestamp Rebasing:** Seamlessly rebases packet PTS/DTS across loop restarts, ensuring timestamps never decrease and video/audio remain tightly synchronized.
- **Prepare Video Once Pipeline:** Inspects source video; if already compatible (H.264 Baseline/Main, AAC, ~2s GOP), remuxes packets without re-encoding. If incompatible, transcodes once using hardware `MediaCodec` and caches with SHA-256 fingerprints.
- **Multi-Live Destination Fan-Out:** Single packet-level reader multiplexes output to multiple independent RTMP/RTMPS YouTube Live destinations, preventing duplicate encoder sessions and isolating network errors.
- **Dedicated Foreground Service:** Runs seamlessly in the background, surviving app minimization and screen-off with a persistent notification.
- **Strict Button-Free Notification:** Per PRD requirements, the persistent foreground notification has ZERO action buttons and no content intent; all controls stay within the app.
- **Security & Privacy:** Stream keys are encrypted at rest using AES-256 GCM backed by the Android Keystore. All diagnostics and logs automatically redact stream keys.

---

## 2. Technical Stack
- **Language & Runtime:** Kotlin, Jetpack Compose, Material 3
- **Android Target:** API 29+ (Android 10+) to API 36
- **Architecture:** Clean MVVM / Modular Architecture
- **Media Engine:** Android `MediaExtractor`, `MediaCodec`, `MediaMuxer`
- **Streaming Engine:** High-performance direct RTMP/RTMPS engine supporting AMF0, FLV tags, AVC SPS/PPS sequence headers, and AAC AudioSpecificConfig
- **Persistence:** Room Database (Metadata, Destinations, History) & DataStore Preferences (Battery Saver, Settings)
- **Security:** Android `KeyStore` with AES-256 GCM

---

## 3. Core Modules
- `com.example.media.inspection.MediaInspector`: Reads container format, MIME codecs, resolution, frame rate, duration, and measures keyframe intervals.
- `com.example.media.preparation.VideoPreparationEngine`: Implements the 10 PRD rules for space estimation, cache verification, remuxing vs hardware transcoding, and output validation.
- `com.example.streaming.rtmp.TimestampRebaser`: Manages timestamp continuity across loop resets.
- `com.example.streaming.rtmp.LoopPacketStreamer`: Reads encoded packets directly from prepared MP4.
- `com.example.streaming.rtmp.RtmpClient`: Connects over plain TCP or TLS (RTMPS), performs RTMP handshake, chunking, and FLV packet transmission.
- `com.example.streaming.rtmp.MultiStreamManager`: Coordinates fan-out to multiple destinations with bounded exponential backoff retries.
- `com.example.service.StreamForegroundService`: Foreground service with persistent button-free notification.
- `com.example.data.security.CryptoManager`: Keystore-backed AES-256 GCM encryption and log redaction.

---

## 4. Building & Running
To run tests locally:
```bash
gradle testDebugUnitTest
```

To build debug APK:
```bash
gradle assembleDebug
```
The output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 5. RTMP Library Integration (RootEncoder / rtmp-rtsp-stream-client-java)

The project includes `com.github.pedroSG94.RootEncoder:library:2.4.5` (the modern, active release of `rtmp-rtsp-stream-client-java`) in `app/build.gradle.kts`.

### Single Stream Session Setup Example (`RootEncoderSession.kt`)
```kotlin
val session = RootEncoderSession(context) { isConnected, error ->
    if (isConnected) {
        Log.d("RTMP", "Live streaming confirmed and published to YouTube!")
    } else if (error != null) {
        Log.e("RTMP", "Stream error: $error")
    }
}

// Start looping stream from local prepared video file
val started = session.startSession(
    preparedVideoFile = File(context.filesDir, "prepared_videos/video.mp4"),
    ingestUrl = "rtmp://a.rtmp.youtube.com/live2",
    streamKey = "xxxx-xxxx-xxxx-xxxx",
    loopVideo = true
)

// When finished:
session.stopSession()
```

