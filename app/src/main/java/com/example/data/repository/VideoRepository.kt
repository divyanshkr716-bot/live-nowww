package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.db.VideoDao
import com.example.data.model.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(
    private val videoDao: VideoDao,
    private val context: Context
) {

    val allVideos: Flow<List<VideoEntity>> = videoDao.getAllVideos()

    suspend fun getVideoById(id: Long): VideoEntity? = withContext(Dispatchers.IO) {
        videoDao.getVideoById(id)
    }

    fun getVideoFlow(id: Long): Flow<VideoEntity?> = videoDao.getVideoFlow(id)

    suspend fun insertVideo(video: VideoEntity): Long = withContext(Dispatchers.IO) {
        videoDao.insertVideo(video)
    }

    suspend fun updateVideo(video: VideoEntity) = withContext(Dispatchers.IO) {
        videoDao.updateVideo(video.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteVideo(video: VideoEntity) = withContext(Dispatchers.IO) {
        // Delete prepared file if present
        video.preparedFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        videoDao.deleteVideo(video)
    }

    suspend fun deletePreparedFileOnly(video: VideoEntity) = withContext(Dispatchers.IO) {
        video.preparedFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        val updated = video.copy(
            preparedFilePath = null,
            preparedFileSize = 0,
            preparationStatus = VideoEntity.STATUS_NOT_PREPARED,
            validationStatus = VideoEntity.VALIDATION_PENDING,
            validationMessage = "Prepared copy deleted by user.",
            preparationFingerprint = null,
            updatedAt = System.currentTimeMillis()
        )
        videoDao.updateVideo(updated)
    }

    suspend fun checkSourceFileAvailable(uriString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
