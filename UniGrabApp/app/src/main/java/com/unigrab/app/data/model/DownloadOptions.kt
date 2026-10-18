package com.unigrab.app.data.model

import com.unigrab.app.data.local.entity.DownloadMode

data class DownloadOptions(
    val downloadMode: DownloadMode,
    val selectedFormatId: String? = null,
    val qualityPreset: String = "best", // "best", "1080p", "720p", "480p", "audio"
    val convertToMp3: Boolean = false,
    val useAria2c: Boolean = true,
    val embedThumbnail: Boolean = true,
    val geoBypass: Boolean = false,
    val subtitles: Boolean = false,
    val threads: Int = 4,
    val proxy: String? = null,
    val cookies: String? = null
)
