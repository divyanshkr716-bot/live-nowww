package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.YouTubeLoopLiveApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class StreamForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_SERVICE) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        // Start Foreground with persistent button-free notification
        val notification = buildPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Observe stream state to stop automatically if all streams are stopped
        serviceScope.launch {
            val app = application as? YouTubeLoopLiveApp ?: return@launch
            app.multiStreamManager.diagnostics.collectLatest { diag ->
                if (!diag.isAnyStreaming) {
                    // All streams finished or stopped from inside app
                    stopForegroundService()
                }
            }
        }

        // START_STICKY so the system can restart if memory pressure clears,
        // but state validation prevents blindly re-streaming if unconfigured.
        return START_STICKY
    }

    private fun stopForegroundService() {
        val app = application as? YouTubeLoopLiveApp
        app?.multiStreamManager?.stopAllStreaming()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Builds the persistent notification adhering to Section 12 requirements:
     * - Title: "YouTube Loop Live"
     * - Text: "Live streaming is running"
     * - Action buttons: NONE
     * - ContentIntent: NONE (no tap action to open app)
     */
    private fun buildPersistentNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YouTube Loop Live")
            .setContentText("Live streaming is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // NO ACTION BUTTONS!
            // NO setContentIntent!
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Loop Live Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing persistent notification while YouTube loop streaming is active."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "yt_loop_live_stream_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.example.service.ACTION_START"
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, StreamForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StreamForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
