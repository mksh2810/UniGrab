package com.unigrab.app.data.download

import android.content.Context
import android.content.Intent
import com.unigrab.app.data.local.entity.DownloadMode
import com.unigrab.app.data.local.entity.DownloadStatus
import com.unigrab.app.data.local.entity.DownloadTask
import com.unigrab.app.data.model.DownloadOptions
import com.unigrab.app.data.model.MediaFormat
import com.unigrab.app.data.model.MediaInfo
import com.unigrab.app.data.repository.UniGrabRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DownloadManager(
    private val repository: UniGrabRepository
) {
    private fun cleanUrl(url: String): String {
        try {
            val uri = java.net.URI(url)
            val host = (uri.host ?: "").lowercase().removePrefix("www.")
            val query = uri.query ?: return url
            
            val params = query.split("&").mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.isNotEmpty()) {
                    val key = parts[0]
                    val value = if (parts.size > 1) parts[1] else ""
                    key to value
                } else null
            }
            
            val keepParams = when {
                host == "youtube.com" || host == "music.youtube.com" -> setOf("v", "list", "t")
                host == "youtu.be" -> setOf("t")
                host == "apple.com" || host == "music.apple.com" || host == "itunes.apple.com" -> setOf("i")
                host == "jiosaavn.com" || host == "open.spotify.com" -> emptySet()
                else -> null
            }
            
            val filtered = if (keepParams != null) {
                params.filter { it.first in keepParams }
            } else {
                val blockTracking = setOf(
                    "utm_source", "utm_medium", "utm_campaign", "utm_term",
                    "utm_content", "si", "feature", "gclid", "fbclid",
                    "referral", "shareid", "sp_cid", "nd"
                )
                params.filter { it.first.lowercase() !in blockTracking }
            }
            
            if (filtered.isEmpty()) {
                return java.net.URI(uri.scheme, uri.authority, uri.path, null, uri.fragment).toString()
            }
            val newQuery = filtered.joinToString("&") { "${it.first}=${it.second}" }
            return java.net.URI(uri.scheme, uri.authority, uri.path, newQuery, uri.fragment).toString()
        } catch (e: Exception) {
            return url
        }
    }

    suspend fun fetchMediaInfo(url: String, initialProxy: String? = null): MediaInfo = withContext(Dispatchers.IO) {
        val cleaned = cleanUrl(url)
        val lowerUrl = cleaned.lowercase()
        val isSpotify = lowerUrl.contains("open.spotify.com")
        val isApple = lowerUrl.contains("music.apple.com") || lowerUrl.contains("itunes.apple.com")

        if (isSpotify) {
            if (cleaned.contains("/track/")) {
                try {
                    val oembedUrl = "https://open.spotify.com/oembed?url=" + java.net.URLEncoder.encode(cleaned, "UTF-8")
                    val connection = java.net.URL(oembedUrl).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(responseText)
                    val rawTitle = json.optString("title", "Spotify Track")
                    val thumbnailUrl = json.optString("thumbnail_url").takeIf { it.isNotEmpty() }
                    
                    val cleanTitle = rawTitle.replace(Regex("\\s*\\|\\s*Spotify\\s*$", RegexOption.IGNORE_CASE), "").trim()
                    var trackTitle = cleanTitle
                    var artistName: String? = null
                    
                    val patterns = listOf(
                        Regex("^(.+?)\\s+-\\s+song and lyrics by\\s+(.+)$", RegexOption.IGNORE_CASE),
                        Regex("^(.+?)\\s+-\\s+song by\\s+(.+)$", RegexOption.IGNORE_CASE),
                        Regex("^(.+?)\\s+by\\s+(.+)$", RegexOption.IGNORE_CASE)
                    )
                    for (pattern in patterns) {
                        val match = pattern.matchEntire(cleanTitle)
                        if (match != null) {
                            trackTitle = match.groupValues[1].trim()
                            artistName = match.groupValues[2].trim()
                            break
                        }
                    }
                    
                    val query = if (!artistName.isNullOrBlank()) {
                        "ytsearch1:$artistName - $trackTitle"
                    } else {
                        "ytsearch1:$trackTitle"
                    }
                    
                    val videoInfo = getInfoWithRetry(query, initialProxy)
                    val formats = videoInfo.formats?.map { format ->
                        MediaFormat(
                            formatId = format.formatId ?: "",
                            ext = format.ext ?: "",
                            resolution = if (format.width > 0 && format.height > 0) "${format.width}x${format.height}" else null,
                            fileSize = format.fileSize,
                            formatNote = format.formatNote,
                            isVideo = format.vcodec != "none" && format.vcodec != null,
                            isAudio = format.acodec != "none" && format.acodec != null,
                            codec = if (format.vcodec != "none" && format.vcodec != null) format.vcodec else format.acodec
                        )
                    } ?: emptyList()
                    
                    return@withContext MediaInfo(
                        url = query,
                        title = trackTitle,
                        uploader = artistName ?: videoInfo.uploader,
                        durationSeconds = videoInfo.duration.toLong(),
                        thumbnailUrl = thumbnailUrl ?: videoInfo.thumbnail,
                        description = videoInfo.description,
                        formats = formats
                    )
                } catch (e: Exception) {
                    throw Exception("Failed to resolve Spotify track metadata: ${e.localizedMessage ?: e.message}")
                }
            } else if (cleaned.contains("/playlist/") || cleaned.contains("/album/")) {
                throw Exception("Spotify playlists and albums require API credentials which are not configured. Currently, only individual Spotify tracks are supported.")
            } else {
                throw Exception("Unsupported Spotify link format. Currently only individual tracks are supported.")
            }
        }

        if (isApple) {
            val trackId: String? = run {
                try {
                    val uri = java.net.URI(cleaned)
                    val query = uri.query ?: ""
                    val params = query.split("&").mapNotNull {
                        val parts = it.split("=", limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else null
                    }.toMap()
                    params["i"] ?: run {
                        val match = Regex("/(\\d+)(?:[/?#]|$)").find(uri.path ?: "")
                        match?.groupValues?.get(1)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            
            if (trackId != null) {
                try {
                    val lookupUrl = "https://itunes.apple.com/lookup?id=$trackId&entity=song"
                    val connection = java.net.URL(lookupUrl).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(responseText)
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        var songObj: org.json.JSONObject? = null
                        for (i in 0 until results.length()) {
                            val item = results.getJSONObject(i)
                            if (item.optString("wrapperType") == "track") {
                                songObj = item
                                break
                            }
                        }
                        if (songObj != null) {
                            val trackName = songObj.getString("trackName")
                            val artistName = songObj.getString("artistName")
                            val artworkUrl = songObj.optString("artworkUrl100").takeIf { it.isNotEmpty() }
                            val query = "ytsearch1:$artistName - $trackName"
                            
                            val videoInfo = getInfoWithRetry(query, initialProxy)
                            val formats = videoInfo.formats?.map { format ->
                                MediaFormat(
                                    formatId = format.formatId ?: "",
                                    ext = format.ext ?: "",
                                    resolution = if (format.width > 0 && format.height > 0) "${format.width}x${format.height}" else null,
                                    fileSize = format.fileSize,
                                    formatNote = format.formatNote,
                                    isVideo = format.vcodec != "none" && format.vcodec != null,
                                    isAudio = format.acodec != "none" && format.acodec != null,
                                    codec = if (format.vcodec != "none" && format.vcodec != null) format.vcodec else format.acodec
                                )
                            } ?: emptyList()
                            
                            return@withContext MediaInfo(
                                url = query,
                                title = trackName,
                                uploader = artistName,
                                durationSeconds = videoInfo.duration.toLong(),
                                thumbnailUrl = artworkUrl ?: videoInfo.thumbnail,
                                description = videoInfo.description,
                                formats = formats
                            )
                        }
                    }
                    throw Exception("No song found with id $trackId in iTunes lookup.")
                } catch (e: Exception) {
                    throw Exception("Failed to resolve Apple Music track metadata: ${e.localizedMessage ?: e.message}")
                }
            } else {
                throw Exception("Apple Music playlists and albums are not supported. Please download tracks individually.")
            }
        }

        val isPlaylist = cleaned.contains("list=") || cleaned.contains("/playlist/") || cleaned.contains("/album/")
        if (isPlaylist) {
            val playlistRequest = YoutubeDLRequest(cleaned)
            playlistRequest.addOption("--flat-playlist")
            playlistRequest.addOption("--dump-json")
            if (!initialProxy.isNullOrBlank()) {
                playlistRequest.addOption("--proxy", initialProxy)
            }

            val entries = mutableListOf<com.unigrab.app.data.model.PlaylistEntry>()
            val outputLines = StringBuilder()
            YoutubeDL.getInstance().execute(playlistRequest, null) { _: Float, _: Long, line: String ->
                if (line.isNotBlank()) {
                    outputLines.appendLine(line)
                }
            }

            for (line in outputLines.toString().lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("{")) {
                    try {
                        val json = org.json.JSONObject(trimmed)
                        val entryId = json.optString("id", "")
                        val entryTitle = json.optString("title", "Unknown Track")
                        val entryUrl = json.optString("webpage_url", "").ifBlank {
                            json.optString("url", "").ifBlank {
                                if (entryId.isNotBlank()) "https://www.youtube.com/watch?v=$entryId" else ""
                            }
                        }
                        val entryUploader = json.optString("uploader", "").ifBlank { null }
                        val entryDuration = json.optDouble("duration", 0.0).toLong()

                        if (entryUrl.isNotBlank()) {
                            entries.add(
                                com.unigrab.app.data.model.PlaylistEntry(
                                    url = entryUrl,
                                    title = entryTitle,
                                    uploader = entryUploader,
                                    durationSeconds = entryDuration
                                )
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            var playlistTitle = "Playlist"
            try {
                val info = getInfoWithRetry(cleaned, initialProxy, flatPlaylist = true)
                playlistTitle = info.title ?: "Playlist"
            } catch (_: Exception) { }

            return@withContext MediaInfo(
                url = cleaned,
                title = playlistTitle,
                uploader = null,
                durationSeconds = 0L,
                thumbnailUrl = null,
                description = null,
                formats = emptyList(),
                playlistEntries = entries.ifEmpty { null }
            )
        }

        val videoInfo = getInfoWithRetry(cleaned, initialProxy)

        val formats = videoInfo.formats?.map { format ->
            MediaFormat(
                formatId = format.formatId ?: "",
                ext = format.ext ?: "",
                resolution = if (format.width > 0 && format.height > 0) "${format.width}x${format.height}" else null,
                fileSize = format.fileSize,
                formatNote = format.formatNote,
                isVideo = format.vcodec != "none" && format.vcodec != null,
                isAudio = format.acodec != "none" && format.acodec != null,
                codec = if (format.vcodec != "none" && format.vcodec != null) format.vcodec else format.acodec
            )
        } ?: emptyList()

        MediaInfo(
            url = cleaned,
            title = videoInfo.title ?: "Unknown Title",
            uploader = videoInfo.uploader,
            durationSeconds = videoInfo.duration.toLong(),
            thumbnailUrl = videoInfo.thumbnail,
            description = videoInfo.description,
            formats = formats
        )
    }

    companion object {
        // Sites that are typically geo-blocked/banned and need a proxy
        private val RESTRICTED_HOSTS = setOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com",
            "youporn.com", "hqporner.com", "spankbang.com", "pornhat.com",
            "rule34.xxx", "rule34.us", "chaturbate.com", "xhamster.com"
        )

        fun isRestrictedSite(host: String?): Boolean {
            if (host == null) return false
            val clean = host.lowercase().removePrefix("www.")
            return RESTRICTED_HOSTS.any { clean.contains(it) }
        }

        fun applyBrowserHeaders(request: YoutubeDLRequest, host: String?) {
            // Inject realistic browser headers and native yt-dlp flags for sites that require impersonation
            request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            request.addOption("--add-header", "Accept:text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            request.addOption("--add-header", "Accept-Language:en-US,en;q=0.5")
            request.addOption("--add-header", "Sec-Fetch-Dest:document")
            request.addOption("--add-header", "Sec-Fetch-Mode:navigate")
            request.addOption("--add-header", "Sec-Fetch-Site:none")
            if (host != null) {
                val clean = host.removePrefix("www.")
                request.addOption("--referer", "https://www.$clean/")
            }
            request.addOption("--no-check-certificates")
            request.addOption("--legacy-server-connect")
        }

        fun isRetryableError(message: String): Boolean {
            val msg = message.lowercase()
            val terms = listOf(
                "10054", "104", "111",
                "connection reset", "connection aborted", "connection refused",
                "transport error", "timed out", "timeout", "recv failure",
                "remote end closed", "errno",
                "403", "forbidden", "unable to download webpage"
            )
            return terms.any { msg.contains(it) }
        }
    }

    private suspend fun getInfoWithRetry(
        cleanedUrl: String,
        initialProxy: String?,
        flatPlaylist: Boolean = false
    ): com.yausername.youtubedl_android.mapper.VideoInfo = withContext(Dispatchers.IO) {
        var success = false
        var currentProxy = initialProxy
        var attempt = 1
        var lastException: Exception? = null
        var videoInfo: com.yausername.youtubedl_android.mapper.VideoInfo? = null

        val host = try { java.net.URL(cleanedUrl).host?.lowercase()?.removePrefix("www.") } catch (e: Exception) { null }
        val isRestricted = isRestrictedSite(host)

        // More attempts for restricted sites since free proxies are unreliable
        val maxAttempts = if (isRestricted) 5 else 3

        // Auto-resolve a proxy for restricted sites if none provided
        if (isRestricted && currentProxy.isNullOrBlank()) {
            val resolvedProxy = AutoProxyResolver.getProxy(cleanedUrl, forceRefresh = false)
            if (resolvedProxy != null) {
                currentProxy = resolvedProxy
            }
        }

        while (!success && attempt <= maxAttempts) {
            try {
                val request = YoutubeDLRequest(cleanedUrl)
                if (flatPlaylist) {
                    request.addOption("--flat-playlist")
                }
                if (!currentProxy.isNullOrBlank()) {
                    request.addOption("--proxy", currentProxy)
                }
                
                if (host?.contains("youtube.com") == true || host?.contains("youtu.be") == true) {
                    request.addOption("--extractor-args", "youtube:player_client=android,web,ios")
                }

                // Inject browser headers for restricted/adult sites
                if (isRestricted) {
                    applyBrowserHeaders(request, host)
                }

                videoInfo = YoutubeDL.getInstance().getInfo(request)
                success = true
            } catch (e: Exception) {
                lastException = e
                val errMessage = e.localizedMessage ?: e.message ?: ""
                val isRetryable = isRetryableError(errMessage)
                
                if (isRetryable && attempt < maxAttempts) {
                    // Invalidate the proxy that just failed before getting a new one
                    AutoProxyResolver.invalidateCurrent()
                    val resolvedProxy = AutoProxyResolver.getProxy(cleanedUrl, forceRefresh = true)
                    if (resolvedProxy != null) {
                        currentProxy = resolvedProxy
                        attempt++
                        continue
                    }
                }
                break
            }
        }

        if (!success || videoInfo == null) {
            throw lastException ?: Exception("Failed to fetch media info")
        }
        videoInfo
    }

    suspend fun enqueueDownload(context: Context, url: String, title: String, options: DownloadOptions) {
        val taskId = UUID.randomUUID().toString()
        val task = DownloadTask(
            id = taskId,
            url = url,
            title = title,
            status = DownloadStatus.PENDING,
            progress = 0f,
            totalSize = 0,
            downloadedBytes = 0,
            speed = "0 B/s",
            eta = "00:00",
            downloadMode = options.downloadMode,
            selectedFormatId = options.selectedFormatId,
            filePath = null,
            error = null,
            createdAt = System.currentTimeMillis()
        )
        repository.insertTask(task)

        // Start Foreground Service
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            putExtra(DownloadService.EXTRA_URL, url)
            putExtra(DownloadService.EXTRA_TITLE, title)
            putExtra(DownloadService.EXTRA_MODE, options.downloadMode.name)
            putExtra(DownloadService.EXTRA_FORMAT_ID, options.selectedFormatId)
            putExtra(DownloadService.EXTRA_PRESET, options.qualityPreset)
            putExtra(DownloadService.EXTRA_CONVERT_MP3, options.convertToMp3)
            putExtra(DownloadService.EXTRA_USE_ARIA2C, options.useAria2c)
            putExtra("extra_embed_thumbnail", options.embedThumbnail)
            putExtra("extra_geo_bypass", options.geoBypass)
            putExtra("extra_subtitles", options.subtitles)
            putExtra("extra_threads", options.threads)
            putExtra("extra_proxy", options.proxy)
            putExtra("extra_cookies", options.cookies)
        }
        context.startForegroundService(intent)
    }

    fun cancelDownload(context: Context, taskId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        context.startForegroundService(intent)
    }

    fun pauseDownload(context: Context, taskId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        context.startForegroundService(intent)
    }

    fun pauseAll(context: Context) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_ALL
            putExtra(DownloadService.EXTRA_TASK_ID, "dummy")
        }
        context.startForegroundService(intent)
    }

    fun stopAll(context: Context) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_STOP_ALL
            putExtra(DownloadService.EXTRA_TASK_ID, "dummy")
        }
        context.startForegroundService(intent)
    }
}
