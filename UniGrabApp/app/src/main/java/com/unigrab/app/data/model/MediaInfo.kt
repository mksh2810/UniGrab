package com.unigrab.app.data.model

data class MediaFormat(
    val formatId: String,
    val ext: String,
    val resolution: String?,
    val fileSize: Long, // in bytes, 0 if unknown
    val formatNote: String?, // e.g. "1080p", "medium"
    val isVideo: Boolean,
    val isAudio: Boolean,
    val codec: String? = null
)

data class PlaylistEntry(
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long
)

data class MediaInfo(
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val description: String?,
    val formats: List<MediaFormat>,
    val playlistEntries: List<PlaylistEntry>? = null
)
