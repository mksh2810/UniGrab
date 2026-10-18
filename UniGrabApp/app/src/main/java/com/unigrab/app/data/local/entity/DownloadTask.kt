package com.unigrab.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    PENDING,
    EXTRACTING,
    DOWNLOADING,
    MERGING,
    COMPLETED,
    FAILED,
    PAUSED,
    CANCELLED
}

enum class DownloadMode {
    AUDIO,
    VIDEO
}

@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey
    val id: String, // Typically UUID or a stable unique ID
    val url: String,
    val title: String,
    val status: DownloadStatus,
    val progress: Float, // 0.0 to 100.0
    val totalSize: Long,
    val downloadedBytes: Long,
    val speed: String, // e.g. "5.4 MiB/s"
    val eta: String, // e.g. "00:34"
    val downloadMode: DownloadMode,
    val selectedFormatId: String?,
    val filePath: String?,
    val error: String?,
    val createdAt: Long,
    val completedAt: Long? = null
)
