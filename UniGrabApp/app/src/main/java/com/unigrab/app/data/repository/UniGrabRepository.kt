package com.unigrab.app.data.repository

import com.unigrab.app.data.local.dao.DownloadTaskDao
import com.unigrab.app.data.local.dao.LibraryDao
import com.unigrab.app.data.local.dao.LogDao
import com.unigrab.app.data.local.entity.DownloadStatus
import com.unigrab.app.data.local.entity.DownloadTask
import com.unigrab.app.data.local.entity.LibraryItem
import com.unigrab.app.data.local.entity.LogEntry
import kotlinx.coroutines.flow.Flow

class UniGrabRepository(
    private val downloadTaskDao: DownloadTaskDao,
    private val libraryDao: LibraryDao,
    private val logDao: LogDao
) {
    // Download Tasks
    val allTasksFlow: Flow<List<DownloadTask>> = downloadTaskDao.getAllTasksFlow()

    suspend fun getAllTasks(): List<DownloadTask> = downloadTaskDao.getAllTasks()

    fun getTaskFlow(id: String): Flow<DownloadTask?> = downloadTaskDao.getTaskFlow(id)

    suspend fun getTaskById(id: String): DownloadTask? = downloadTaskDao.getTaskById(id)

    suspend fun insertTask(task: DownloadTask) = downloadTaskDao.insertTask(task)

    suspend fun updateTask(task: DownloadTask) = downloadTaskDao.updateTask(task)

    suspend fun deleteTask(task: DownloadTask) = downloadTaskDao.deleteTask(task)

    suspend fun updateProgress(
        id: String,
        progress: Float,
        downloadedBytes: Long,
        totalSize: Long,
        speed: String,
        eta: String
    ) = downloadTaskDao.updateProgress(id, progress, downloadedBytes, totalSize, speed, eta)

    suspend fun updateStatus(
        id: String,
        status: DownloadStatus,
        filePath: String? = null,
        error: String? = null,
        completedAt: Long? = null
    ) = downloadTaskDao.updateStatus(id, status, filePath, error, completedAt)

    // Library Items
    val allLibraryItemsFlow: Flow<List<LibraryItem>> = libraryDao.getAllItemsFlow()

    suspend fun getAllLibraryItems(): List<LibraryItem> = libraryDao.getAllItems()

    suspend fun getLibraryItemById(id: Long): LibraryItem? = libraryDao.getItemById(id)

    suspend fun getLibraryItemByPath(filePath: String): LibraryItem? = libraryDao.getItemByPath(filePath)

    suspend fun insertLibraryItem(item: LibraryItem): Long = libraryDao.insertItem(item)

    suspend fun deleteLibraryItem(item: LibraryItem) = libraryDao.deleteItem(item)

    suspend fun deleteLibraryItemById(id: Long) = libraryDao.deleteItemById(id)

    // Logs
    fun getLogsForTaskFlow(taskId: String): Flow<List<LogEntry>> = logDao.getLogsForTaskFlow(taskId)

    suspend fun getLogsForTask(taskId: String): List<LogEntry> = logDao.getLogsForTask(taskId)

    suspend fun insertLog(taskId: String, line: String) {
        val entry = LogEntry(
            taskId = taskId,
            line = line,
            timestamp = System.currentTimeMillis()
        )
        logDao.insertLog(entry)
    }

    suspend fun deleteLogsForTask(taskId: String) = logDao.deleteLogsForTask(taskId)
}
