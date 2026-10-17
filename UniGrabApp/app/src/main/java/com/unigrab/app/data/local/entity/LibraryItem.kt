package com.unigrab.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_items")
data class LibraryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: String?, // Links back to the source download task, if any
    val title: String,
    val artist: String?, // Uploader / Artist
    val sourceUrl: String?,
    val filePath: String, // Full path on disk
    val fileSize: Long, // in bytes
    val mimeType: String, // e.g. "video/mp4", "audio/mp3"
    val durationSeconds: Long, // 0 if unknown
    val downloadMode: DownloadMode,
    val thumbnailPath: String?, // Local cached image path or URL
    val addedAt: Long
)
