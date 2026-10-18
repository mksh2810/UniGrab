package com.unigrab.app.data.local.dao

import androidx.room.*
import com.unigrab.app.data.local.entity.DownloadStatus
import com.unigrab.app.data.local.entity.DownloadTask
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    suspend fun getAllTasks(): List<DownloadTask>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    fun getTaskFlow(id: String): Flow<DownloadTask?>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): DownloadTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTask)

    @Update
    suspend fun updateTask(task: DownloadTask)

    @Delete
    suspend fun deleteTask(task: DownloadTask)

    @Query("UPDATE download_tasks SET progress = :progress, downloadedBytes = :downloadedBytes, totalSize = :totalSize, speed = :speed, eta = :eta WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        progress: Float,
        downloadedBytes: Long,
        totalSize: Long,
        speed: String,
        eta: String
    )

    @Query("UPDATE download_tasks SET status = :status, filePath = :filePath, error = :error, completedAt = :completedAt WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: DownloadStatus,
        filePath: String?,
        error: String?,
        completedAt: Long?
    )
}
