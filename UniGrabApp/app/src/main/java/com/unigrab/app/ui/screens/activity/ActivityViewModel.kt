package com.unigrab.app.ui.screens.activity

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.unigrab.app.data.download.DownloadManager
import com.unigrab.app.data.local.entity.DownloadStatus
import com.unigrab.app.data.local.entity.DownloadTask
import com.unigrab.app.data.local.entity.LogEntry
import com.unigrab.app.data.model.DownloadOptions
import com.unigrab.app.data.repository.UniGrabRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActivityViewModel(
    private val repository: UniGrabRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    val tasks: StateFlow<List<DownloadTask>> = repository.allTasksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun cancelTask(context: Context, taskId: String) {
        downloadManager.cancelDownload(context, taskId)
    }

    fun pauseTask(context: Context, taskId: String) {
        downloadManager.pauseDownload(context, taskId)
    }

    fun resumeTask(context: Context, task: DownloadTask) {
        viewModelScope.launch {
            repository.updateStatus(task.id, DownloadStatus.PENDING)
            val prefs = context.getSharedPreferences("unigrab_prefs", Context.MODE_PRIVATE)
            val proxy = prefs.getString("proxy_url", null).takeIf { !it.isNullOrBlank() }
            val cookies = prefs.getString("cookies_path", null).takeIf { !it.isNullOrBlank() }
            
            val options = DownloadOptions(
                downloadMode = task.downloadMode,
                selectedFormatId = task.selectedFormatId,
                threads = 4,
                proxy = proxy,
                cookies = cookies
            )
            downloadManager.enqueueDownload(context, task.url, task.title, options)
        }
    }

    fun pauseAll(context: Context) {
        downloadManager.pauseAll(context)
    }

    fun stopAll(context: Context) {
        downloadManager.stopAll(context)
    }

    fun deleteTask(task: DownloadTask) {
        viewModelScope.launch {
            repository.deleteTask(task)
            repository.deleteLogsForTask(task.id)
        }
    }

    fun getLogsForTask(taskId: String): Flow<List<LogEntry>> {
        return repository.getLogsForTaskFlow(taskId)
    }

    class Factory(
        private val repository: UniGrabRepository,
        private val downloadManager: DownloadManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ActivityViewModel(repository, downloadManager) as T
        }
    }
}
