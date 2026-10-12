package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.StreamDestinationEntity
import com.example.data.model.StreamHistoryEntity
import com.example.data.model.VideoEntity

@Database(
    entities = [
        VideoEntity::class,
        StreamDestinationEntity::class,
        StreamHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun streamDestinationDao(): StreamDestinationDao
    abstract fun streamHistoryDao(): StreamHistoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "youtube_loop_live.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
            }
        }
    }
}
