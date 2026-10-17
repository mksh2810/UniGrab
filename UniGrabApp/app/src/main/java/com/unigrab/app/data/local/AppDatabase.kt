package com.unigrab.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.unigrab.app.data.local.dao.DownloadTaskDao
import com.unigrab.app.data.local.dao.LibraryDao
import com.unigrab.app.data.local.dao.LogDao
import com.unigrab.app.data.local.entity.DownloadMode
import com.unigrab.app.data.local.entity.DownloadStatus
import com.unigrab.app.data.local.entity.DownloadTask
import com.unigrab.app.data.local.entity.LibraryItem
import com.unigrab.app.data.local.entity.LogEntry

class Converters {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DownloadStatus = try {
        DownloadStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        DownloadStatus.FAILED
    }

    @TypeConverter
    fun fromMode(mode: DownloadMode): String = mode.name

    @TypeConverter
    fun toMode(value: String): DownloadMode = try {
        DownloadMode.valueOf(value)
    } catch (e: IllegalArgumentException) {
        DownloadMode.VIDEO
    }
}

@Database(
    entities = [DownloadTask::class, LibraryItem::class, LogEntry::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun libraryDao(): LibraryDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "unigrab_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
