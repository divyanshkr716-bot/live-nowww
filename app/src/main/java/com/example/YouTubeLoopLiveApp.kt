package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.repository.DestinationRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.VideoRepository
import com.example.data.security.CryptoManager
import com.example.streaming.rtmp.MultiStreamManager

class YouTubeLoopLiveApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var cryptoManager: CryptoManager
        private set

    lateinit var videoRepository: VideoRepository
        private set

    lateinit var destinationRepository: DestinationRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var multiStreamManager: MultiStreamManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        cryptoManager = CryptoManager(this)
        videoRepository = VideoRepository(database.videoDao(), this)
        destinationRepository = DestinationRepository(database.streamDestinationDao(), database.streamHistoryDao(), cryptoManager)
        settingsRepository = SettingsRepository(this)
        multiStreamManager = MultiStreamManager(this, destinationRepository, videoRepository, settingsRepository)
    }

    companion object {
        lateinit var instance: YouTubeLoopLiveApp
            private set
    }
}
