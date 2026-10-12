package com.example.data.repository

import com.example.data.db.StreamDestinationDao
import com.example.data.db.StreamHistoryDao
import com.example.data.model.StreamDestinationEntity
import com.example.data.model.StreamHistoryEntity
import com.example.data.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DestinationRepository(
    private val destinationDao: StreamDestinationDao,
    private val historyDao: StreamHistoryDao,
    private val cryptoManager: CryptoManager
) {

    val allDestinations: Flow<List<StreamDestinationEntity>> = destinationDao.getAllDestinations()
    val enabledDestinations: Flow<List<StreamDestinationEntity>> = destinationDao.getEnabledDestinations()
    val allHistory: Flow<List<StreamHistoryEntity>> = historyDao.getAllHistory()

    suspend fun getDestinationById(id: Long): StreamDestinationEntity? = withContext(Dispatchers.IO) {
        destinationDao.getDestinationById(id)
    }

    fun getDestinationFlow(id: Long): Flow<StreamDestinationEntity?> = destinationDao.getDestinationFlow(id)

    suspend fun saveDestination(
        destinationId: Long = 0,
        displayName: String,
        ingestUrl: String,
        plainStreamKey: String,
        selectedVideoId: Long?,
        enabled: Boolean = true,
        maxRetries: Int = 5,
        backoffInitialSec: Int = 2
    ): Long = withContext(Dispatchers.IO) {
        val encryptedKey = if (plainStreamKey.isNotBlank()) {
            cryptoManager.encrypt(plainStreamKey)
        } else {
            // Keep existing if editing and left blank
            if (destinationId > 0) {
                destinationDao.getDestinationById(destinationId)?.encryptedStreamKeyReference ?: ""
            } else ""
        }

        val entity = StreamDestinationEntity(
            destinationId = destinationId,
            displayName = displayName,
            ingestUrl = ingestUrl.trim(),
            encryptedStreamKeyReference = encryptedKey,
            selectedVideoId = selectedVideoId,
            enabled = enabled,
            maxRetries = maxRetries,
            backoffInitialSec = backoffInitialSec,
            updatedAt = System.currentTimeMillis()
        )

        if (destinationId > 0) {
            destinationDao.updateDestination(entity)
            destinationId
        } else {
            destinationDao.insertDestination(entity)
        }
    }

    suspend fun deleteDestination(destination: StreamDestinationEntity) = withContext(Dispatchers.IO) {
        destinationDao.deleteDestination(destination)
    }

    suspend fun getDecryptedStreamKey(destination: StreamDestinationEntity): String = withContext(Dispatchers.IO) {
        cryptoManager.decrypt(destination.encryptedStreamKeyReference)
    }

    suspend fun logHistory(history: StreamHistoryEntity): Long = withContext(Dispatchers.IO) {
        historyDao.insertHistory(history)
    }

    suspend fun updateHistory(history: StreamHistoryEntity) = withContext(Dispatchers.IO) {
        historyDao.updateHistory(history)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyDao.clearAllHistory()
    }
}
