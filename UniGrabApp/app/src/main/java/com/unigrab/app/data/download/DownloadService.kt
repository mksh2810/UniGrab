package com.unigrab.app.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.unigrab.app.MainActivity
import com.unigrab.app.UniGrabApplication
import com.unigrab.app.data.local.entity.DownloadMode
import com.unigrab.app.data.local.entity.DownloadStatus
import com.unigrab.app.data.local.entity.DownloadTask
import com.unigrab.app.data.local.entity.LibraryItem
import com.unigrab.app.data.repository.UniGrabRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {

    companion object {
        const val ACTION_START = "com.unigrab.app.action.START"
        const val ACTION_CANCEL = "com.unigrab.app.action.CANCEL"
        const val ACTION_PAUSE = "com.unigrab.app.action.PAUSE"
        const val ACTION_PAUSE_ALL = "com.unigrab.app.action.PAUSE_ALL"
        const val ACTION_STOP_ALL = "com.unigrab.app.action.STOP_ALL"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_FORMAT_ID = "extra_format_id"
        const val EXTRA_PRESET = "extra_preset"
        const val EXTRA_CONVERT_MP3 = "extra_convert_mp3"
        const val EXTRA_USE_ARIA2C = "extra_use_aria2c"
        
        const val EXTRA_EMBED_THUMBNAIL = "extra_embed_thumbnail"
        const val EXTRA_GEO_BYPASS = "extra_geo_bypass"
        const val EXTRA_SUBTITLES = "extra_subtitles"
        const val EXTRA_THREADS = "extra_threads"
        const val EXTRA_PROXY = "extra_proxy"
        const val EXTRA_COOKIES = "extra_cookies"

        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var repository: UniGrabRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingIntentsQueue = java.util.Collections.synchronizedList(mutableListOf<Intent>())
    private val activeJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val activeTasks = ConcurrentHashMap<String, DownloadTask>()

    override fun onCreate() {
        super.onCreate()
        val app = application as UniGrabApplication
        repository = app.repository
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY

        // Start foreground service synchronously immediately to comply with Android 14+ rules
        val initialNotif = createInitialNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        if (action == ACTION_CANCEL) {
            cancelTask(taskId)
        } else if (action == ACTION_PAUSE) {
            pauseTask(taskId)
        } else if (action == ACTION_PAUSE_ALL) {
            pauseAllTasks()
        } else if (action == ACTION_STOP_ALL) {
            stopAllTasks()
        } else if (action == ACTION_START) {
            // Add to queue and process
            synchronized(pendingIntentsQueue) {
                // Prevent duplicate intents for the same taskId in the queue
                val alreadyQueued = pendingIntentsQueue.any { it.getStringExtra(EXTRA_TASK_ID) == taskId }
                if (!alreadyQueued) {
                    pendingIntentsQueue.add(intent)
                }
            }
            processQueue()
        }

        return START_NOT_STICKY
    }

    private fun processQueue() {
        synchronized(pendingIntentsQueue) {
            if (activeJobs.size >= 1) {
                // Already downloading one task. The rest remain queued.
                return
            }
            if (pendingIntentsQueue.isEmpty()) {
                checkStopSelf()
                return
            }
            val intent = pendingIntentsQueue.removeAt(0)
            val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
            val url = intent.getStringExtra(EXTRA_URL) ?: return
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "Download"
            val modeName = intent.getStringExtra(EXTRA_MODE) ?: DownloadMode.VIDEO.name
            val mode = DownloadMode.valueOf(modeName)
            val formatId = intent.getStringExtra(EXTRA_FORMAT_ID)
            val preset = intent.getStringExtra(EXTRA_PRESET) ?: "best"
            val convertMp3 = intent.getBooleanExtra(EXTRA_CONVERT_MP3, false)
            val useAria2c = intent.getBooleanExtra(EXTRA_USE_ARIA2C, true)
            val embedThumbnail = intent.getBooleanExtra(EXTRA_EMBED_THUMBNAIL, true)
            val geoBypass = intent.getBooleanExtra(EXTRA_GEO_BYPASS, false)
            val subtitles = intent.getBooleanExtra(EXTRA_SUBTITLES, false)
            val threads = intent.getIntExtra(EXTRA_THREADS, 4)
            val proxy = intent.getStringExtra(EXTRA_PROXY)
            val cookies = intent.getStringExtra(EXTRA_COOKIES)

            startTaskImpl(
                taskId = taskId,
                url = url,
                title = title,
                mode = mode,
                formatId = formatId,
                preset = preset,
                convertMp3 = convertMp3,
                useAria2c = useAria2c,
                embedThumbnail = embedThumbnail,
                geoBypass = geoBypass,
                subtitles = subtitles,
                threads = threads,
                proxy = proxy,
                cookies = cookies
            )
        }
    }

    private fun createInitialNotification(): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Preparing Download")
            .setContentText("UniGrab is starting the media download...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startTaskImpl(
        taskId: String,
        url: String,
        title: String,
        mode: DownloadMode,
        formatId: String?,
        preset: String,
        convertMp3: Boolean,
        useAria2c: Boolean,
        embedThumbnail: Boolean,
        geoBypass: Boolean,
        subtitles: Boolean,
        threads: Int,
        proxy: String?,
        cookies: String?
    ) {
        val job = serviceScope.launch {
            try {
                val initialTask = repository.getTaskById(taskId) ?: DownloadTask(
                    id = taskId,
                    url = url,
                    title = title,
                    status = DownloadStatus.PENDING,
                    progress = 0f,
                    totalSize = 0,
                    downloadedBytes = 0,
                    speed = "0 B/s",
                    eta = "00:00",
                    downloadMode = mode,
                    selectedFormatId = formatId,
                    filePath = null,
                    error = null,
                    createdAt = System.currentTimeMillis()
                )
                activeTasks[taskId] = initialTask
                updateNotification()

                repository.updateStatus(taskId, DownloadStatus.DOWNLOADING)
                activeTasks[taskId] = activeTasks[taskId]!!.copy(status = DownloadStatus.DOWNLOADING)

                val prefs = getSharedPreferences("unigrab_prefs", Context.MODE_PRIVATE)
                val customPath = prefs.getString("download_directory", null)
                val baseDir = if (!customPath.isNullOrBlank()) {
                    File(customPath)
                } else {
                    val systemDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    File(systemDownloadDir, "UniGrab")
                }
                val subFolder = if (mode == DownloadMode.AUDIO) "audio" else "video"
                val downloadDir = File(baseDir, subFolder)
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val host = try { java.net.URL(url).host?.lowercase()?.removePrefix("www.") } catch (e: Exception) { null }
                val directHosts = setOf(
                    "youtube.com", "youtu.be", "music.youtube.com",
                    "soundcloud.com", "bandcamp.com", "vimeo.com",
                    "instagram.com", "tiktok.com", "facebook.com",
                    "twitter.com", "x.com", "dailymotion.com", "dai.ly"
                )
                val isDirectHost = host != null && directHosts.any { host.contains(it) }

                val template = "${downloadDir.absolutePath}/%(title)s.%(ext)s"

                fun buildRequest(targetUrl: String, targetProxy: String?, includeThumbnail: Boolean): YoutubeDLRequest {
                    val req = YoutubeDLRequest(targetUrl)
                    req.addOption("--no-mtime")
                    req.addOption("-o", template)

                    if (useAria2c) {
                        req.addOption("--downloader", "aria2c")
                        req.addOption("--downloader-args", "aria2c:-c -j $threads -x $threads -s $threads -k 1M")
                    } else {
                        req.addOption("--concurrent-fragments", threads)
                    }

                    // Restrict filenames to ASCII characters to prevent emojis, spaces, and full-width forms in filenames
                    req.addOption("--restrict-filenames")

                    if (host?.contains("youtube.com") == true || host?.contains("youtu.be") == true) {
                        req.addOption("--extractor-args", "youtube:player_client=web_safari,web_embedded,default")
                    }

                    if (includeThumbnail) {
                        req.addOption("--embed-thumbnail")
                    }
                    if (geoBypass) {
                        req.addOption("--geo-bypass")
                    }
                    if (subtitles && mode == DownloadMode.VIDEO) {
                        req.addOption("--write-subs")
                        req.addOption("--all-subs")
                    }
                    if (!targetProxy.isNullOrBlank()) {
                        req.addOption("--proxy", targetProxy)
                    }
                    if (!cookies.isNullOrBlank()) {
                        val cookiesFile = File(cookies)
                        if (cookiesFile.exists()) {
                            req.addOption("--cookies", cookies)
                        }
                    }

                    // Inject browser-like headers for restricted/adult sites to work around impersonation requirement
                    if (isRestricted) {
                        DownloadManager.applyBrowserHeaders(req, host)
                    }

                    if (mode == DownloadMode.AUDIO) {
                        val finalFormat = formatId ?: "mp3"
                        val isExtensionFormat = finalFormat in setOf("mp3", "m4a", "aac", "wav", "flac", "opus", "ogg")
                        if (isExtensionFormat) {
                            req.addOption("-f", "bestaudio/best")
                            req.addOption("-x")
                            req.addOption("--audio-format", finalFormat)
                            req.addOption("--audio-quality", "0")
                        } else {
                            if (finalFormat != "best") {
                                req.addOption("-f", finalFormat)
                            } else {
                                req.addOption("-f", "bestaudio/best")
                                req.addOption("-x")
                                req.addOption("--audio-format", "flac")
                                req.addOption("--audio-quality", "0")
                            }
                        }
                    } else {
                        val isExtensionFormat = formatId in setOf("mp4", "webm", "mkv")
                        val formatSpec = when (preset) {
                            "1080p" -> "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                            "720p" -> "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]/best"
                            "480p" -> "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=480]+bestaudio/best[height<=480]/best"
                            else -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best"
                        }
                        req.addOption("-f", formatSpec)
                        if (isExtensionFormat) {
                            req.addOption("--merge-output-format", formatId!!)
                        } else {
                            req.addOption("--merge-output-format", "mp4")
                        }
                    }
                    return req
                }

                var capturedDestPath: String? = null
                val mediaExtensions = setOf("mp3", "m4a", "ogg", "wav", "flac", "opus", "mp4", "webm", "mkv", "avi", "3gp")

                var success = false
                var currentProxy = proxy
                var attempt = 1
                val isRestricted = DownloadManager.isRestrictedSite(host)
                val maxAttempts = if (isRestricted) 5 else 3

                // Auto-resolve a proxy for restricted/banned sites if no proxy configured
                if (isRestricted && currentProxy.isNullOrBlank()) {
                    val resolvedProxy = AutoProxyResolver.getProxy(url, forceRefresh = false)
                    if (resolvedProxy != null) {
                        currentProxy = resolvedProxy
                        serviceScope.launch {
                            repository.insertLog(taskId, "Auto-proxy enabled for restricted site")
                        }
                    }
                }

                // Only embed thumbnail initially if it's a known direct host to prevent KeyError: 'streams'
                val shouldEmbedThumbnailInitially = embedThumbnail && isDirectHost

                while (!success && attempt <= maxAttempts) {
                    try {
                        val activeRequest = buildRequest(url, currentProxy, shouldEmbedThumbnailInitially)
                        
                        // Execute the download
                        YoutubeDL.getInstance().execute(activeRequest, taskId, { progress, etaInSeconds, line ->
                            val etaStr = if (etaInSeconds > 0) {
                                val m = etaInSeconds / 60
                                val s = etaInSeconds % 60
                                String.format("%02d:%02d", m, s)
                            } else "00:00"

                            if (line.isNotBlank()) {
                                val lowerLine = line.lowercase()
                                if (lowerLine.contains("destination:") || lowerLine.contains("merging formats into")) {
                                    val pathPart = when {
                                        line.contains("Destination: ") -> line.substringAfter("Destination: ")
                                        line.contains("Merging formats into ") -> line.substringAfter("Merging formats into ")
                                        else -> null
                                    }
                                    if (pathPart != null) {
                                        val cleanPath = pathPart.trim('"', '\'')
                                        if (cleanPath.isNotBlank()) {
                                            val ext = File(cleanPath).extension.lowercase()
                                            if (ext in mediaExtensions) {
                                                capturedDestPath = cleanPath
                                            }
                                        }
                                    }
                                }
                            }

                            serviceScope.launch {
                                if (line.isNotBlank() && shouldLogLine(line)) {
                                    repository.insertLog(taskId, line)
                                }
                                repository.updateProgress(
                                    id = taskId,
                                    progress = progress,
                                    downloadedBytes = 0,
                                    totalSize = 0,
                                    speed = "Downloading...",
                                    eta = etaStr
                                )
                                activeTasks[taskId] = activeTasks[taskId]?.copy(
                                    progress = progress,
                                    speed = "Downloading...",
                                    eta = etaStr
                                ) ?: return@launch
                                updateNotification()
                            }
                        })
                        success = true
                    } catch (e: Throwable) {
                        val isCancelled = (activeJobs[taskId]?.isCancelled == true) || (e is kotlinx.coroutines.CancellationException)
                        if (isCancelled) throw e
                        
                        val errMessage = e.localizedMessage ?: e.message ?: ""
                        val isRetryable = DownloadManager.isRetryableError(errMessage)
                        
                        if (isRetryable && attempt < maxAttempts) {
                            serviceScope.launch {
                                repository.insertLog(taskId, "Error: $errMessage. Rotating proxy (Attempt $attempt/$maxAttempts)...")
                            }
                            AutoProxyResolver.invalidateCurrent()
                            val resolvedProxy = AutoProxyResolver.getProxy(url, forceRefresh = true)
                            if (resolvedProxy != null) {
                                currentProxy = resolvedProxy
                                attempt++
                                continue
                            }
                        }
                        throw e
                    }
                }

                var downloadedFile: File? = null
                if (!capturedDestPath.isNullOrBlank()) {
                    val file = File(capturedDestPath!!)
                    if (file.exists()) {
                        downloadedFile = file
                    }
                }

                val files = downloadDir.listFiles()
                if (downloadedFile == null) {
                    if (files != null) {
                        val recentFiles = files.filter { 
                            !it.name.endsWith(".part") && 
                            !it.name.endsWith(".ytdl") &&
                            it.extension.lowercase() in mediaExtensions &&
                            (System.currentTimeMillis() - it.lastModified() < 300_000)
                        }.sortedByDescending { it.lastModified() }
                        
                        downloadedFile = recentFiles.firstOrNull()
                    }
                }

                if (downloadedFile != null && downloadedFile.exists()) {
                    if (!isDirectHost && downloadedFile!!.length() in 1..8192) {
                        val content = try { downloadedFile!!.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
                        if (content.trim().startsWith("#EXTM3U")) {
                            val hlsUrl = content.split('\n')
                                .map { it.trim() }
                                .firstOrNull { it.startsWith("http") }
                            if (hlsUrl != null) {
                                serviceScope.launch {
                                    repository.insertLog(taskId, "Detected HLS manifest disguised as video — re-downloading from stream...")
                                }
                                downloadedFile!!.delete()
                                
                                val hlsRequest = buildRequest(hlsUrl, currentProxy, embedThumbnail)
                                var hlsSuccess = false
                                var hlsAttempt = 1
                                while (!hlsSuccess && hlsAttempt <= maxAttempts) {
                                    try {
                                        val runRequest = if (hlsAttempt == 1) hlsRequest else buildRequest(hlsUrl, currentProxy, embedThumbnail)
                                        YoutubeDL.getInstance().execute(runRequest, taskId, { progress, etaInSeconds, line ->
                                            val etaStr = if (etaInSeconds > 0) {
                                                val m = etaInSeconds / 60
                                                val s = etaInSeconds % 60
                                                String.format("%02d:%02d", m, s)
                                            } else "00:00"

                                            if (line.isNotBlank()) {
                                                val lowerLine = line.lowercase()
                                                if (lowerLine.contains("destination:") || lowerLine.contains("merging formats into")) {
                                                    val pathPart = when {
                                                        line.contains("Destination: ") -> line.substringAfter("Destination: ")
                                                        line.contains("Merging formats into ") -> line.substringAfter("Merging formats into ")
                                                        else -> null
                                                    }
                                                    if (pathPart != null) {
                                                        val cleanPath = pathPart.trim('"', '\'')
                                                        if (cleanPath.isNotBlank()) {
                                                            val ext = File(cleanPath).extension.lowercase()
                                                            if (ext in mediaExtensions) {
                                                                    capturedDestPath = cleanPath
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            serviceScope.launch {
                                                if (line.isNotBlank() && shouldLogLine(line)) {
                                                    repository.insertLog(taskId, line)
                                                }
                                                repository.updateProgress(
                                                    id = taskId,
                                                    progress = progress,
                                                    downloadedBytes = 0,
                                                    totalSize = 0,
                                                    speed = "Downloading...",
                                                    eta = etaStr
                                                )
                                                activeTasks[taskId] = activeTasks[taskId]?.copy(
                                                    progress = progress,
                                                    speed = "Downloading...",
                                                    eta = etaStr
                                                ) ?: return@launch
                                                updateNotification()
                                            }
                                        })
                                        hlsSuccess = true
                                    } catch (e: Throwable) {
                                        val isCancelled = (activeJobs[taskId]?.isCancelled == true) || (e is kotlinx.coroutines.CancellationException)
                                        if (isCancelled) throw e
                                        
                                        val errMessage = e.localizedMessage ?: e.message ?: ""
                                        val isRetryable = DownloadManager.isRetryableError(errMessage)
                                        
                                        if (isRetryable && hlsAttempt < maxAttempts) {
                                            serviceScope.launch {
                                                repository.insertLog(taskId, "HLS error: $errMessage. Rotating proxy (Attempt $hlsAttempt/$maxAttempts)...")
                                            }
                                            AutoProxyResolver.invalidateCurrent()
                                            val resolvedProxy = AutoProxyResolver.getProxy(hlsUrl, forceRefresh = true)
                                            if (resolvedProxy != null) {
                                                currentProxy = resolvedProxy
                                                hlsAttempt++
                                                continue
                                            }
                                        }
                                        throw e
                                    }
                                }
                                
                                // Re-retrieve final downloaded file
                                if (!capturedDestPath.isNullOrBlank()) {
                                    val file = File(capturedDestPath!!)
                                    if (file.exists()) {
                                        downloadedFile = file
                                    }
                                }
                            }
                        }
                    }

                    val filePath = downloadedFile!!.absolutePath
                    val fileSize = downloadedFile!!.length()

                    repository.updateStatus(taskId, DownloadStatus.COMPLETED, filePath = filePath)
                    activeTasks.remove(taskId)

                    val libItem = LibraryItem(
                        taskId = taskId,
                        title = title,
                        artist = null,
                        sourceUrl = url,
                        filePath = filePath,
                        fileSize = fileSize,
                        mimeType = getMimeType(downloadedFile!!),
                        durationSeconds = 0,
                        downloadMode = mode,
                        thumbnailPath = null,
                        addedAt = System.currentTimeMillis()
                    )
                    repository.insertLibraryItem(libItem)
                } else {
                    val fileListStr = files?.joinToString { "${it.name} (${it.length()}B)" } ?: "null"
                    throw Exception("Downloaded file not found on disk. Found files in target folder: $fileListStr")
                }

            } catch (e: Throwable) {
                val isCancelled = (activeJobs[taskId]?.isCancelled == true) || (e is kotlinx.coroutines.CancellationException)
                if (isCancelled) {
                    repository.updateStatus(taskId, DownloadStatus.CANCELLED)
                } else {
                    repository.insertLog(taskId, "ERROR: ${e.localizedMessage ?: e.message ?: "Download failed"}")
                    repository.updateStatus(taskId, DownloadStatus.FAILED, error = e.localizedMessage ?: e.message ?: "Download failed")
                }
                activeTasks.remove(taskId)
            } finally {
                activeJobs.remove(taskId)
                updateNotification()
                processQueue()
            }
        }
        activeJobs[taskId] = job
    }

    private fun cancelTask(taskId: String) {
        synchronized(pendingIntentsQueue) {
            val iterator = pendingIntentsQueue.iterator()
            while (iterator.hasNext()) {
                val intent = iterator.next()
                if (intent.getStringExtra(EXTRA_TASK_ID) == taskId) {
                    iterator.remove()
                }
            }
        }
        val job = activeJobs[taskId]
        job?.cancel()
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (e: Exception) {
            // Ignore
        }
        if (job == null) {
            serviceScope.launch {
                repository.updateStatus(taskId, DownloadStatus.CANCELLED)
                activeTasks.remove(taskId)
                updateNotification()
                processQueue()
            }
        }
    }

    private fun pauseTask(taskId: String) {
        synchronized(pendingIntentsQueue) {
            val iterator = pendingIntentsQueue.iterator()
            while (iterator.hasNext()) {
                val intent = iterator.next()
                if (intent.getStringExtra(EXTRA_TASK_ID) == taskId) {
                    iterator.remove()
                }
            }
        }
        val job = activeJobs[taskId]
        job?.cancel()
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (e: Exception) {
            // Ignore
        }
        serviceScope.launch {
            repository.updateStatus(taskId, DownloadStatus.PAUSED)
            activeTasks.remove(taskId)
            updateNotification()
            processQueue()
        }
    }

    private fun pauseAllTasks() {
        synchronized(pendingIntentsQueue) {
            pendingIntentsQueue.clear()
        }
        val keys = activeJobs.keys.toList()
        for (taskId in keys) {
            activeJobs[taskId]?.cancel()
            try {
                YoutubeDL.getInstance().destroyProcessById(taskId)
            } catch (e: Exception) {
                // Ignore
            }
        }
        serviceScope.launch {
            for (taskId in keys) {
                repository.updateStatus(taskId, DownloadStatus.PAUSED)
                activeTasks.remove(taskId)
            }
            // Also pause any database tasks marked as pending
            val pendingTasks = repository.getAllTasks().filter { it.status == DownloadStatus.PENDING }
            for (task in pendingTasks) {
                repository.updateStatus(task.id, DownloadStatus.PAUSED)
            }
            updateNotification()
            processQueue()
        }
    }

    private fun stopAllTasks() {
        synchronized(pendingIntentsQueue) {
            pendingIntentsQueue.clear()
        }
        val keys = activeJobs.keys.toList()
        for (taskId in keys) {
            activeJobs[taskId]?.cancel()
            try {
                YoutubeDL.getInstance().destroyProcessById(taskId)
            } catch (e: Exception) {
                // Ignore
            }
        }
        serviceScope.launch {
            for (taskId in keys) {
                repository.updateStatus(taskId, DownloadStatus.CANCELLED)
                activeTasks.remove(taskId)
            }
            // Also cancel any pending tasks
            val pendingTasks = repository.getAllTasks().filter { it.status == DownloadStatus.PENDING }
            for (task in pendingTasks) {
                repository.updateStatus(task.id, DownloadStatus.CANCELLED)
            }
            updateNotification()
            processQueue()
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "mp3" -> "audio/mp3"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "video/*"
        }
    }

    private fun checkStopSelf() {
        serviceScope.launch(Dispatchers.Main) {
            if (activeTasks.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "UniGrab Downloads"
            val descriptionText = "Shows active download progress"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        serviceScope.launch(Dispatchers.Main) {
            if (activeTasks.isEmpty()) {
                return@launch
            }

            val taskCount = activeTasks.size
            val firstTask = activeTasks.values.firstOrNull() ?: return@launch
            val progress = firstTask.progress.toInt()

            val text = if (taskCount > 1) {
                "Downloading $taskCount items ($progress%)"
            } else {
                "${firstTask.title} ($progress%)"
            }

            val notificationIntent = Intent(this@DownloadService, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this@DownloadService, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                .setContentTitle("Downloading Media")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, false)
                .setContentIntent(pendingIntent)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun shouldLogLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()
        // Skip progress percentage lines
        if (lower.contains("[download]") && (lower.contains("%") || lower.contains("eta"))) {
            return false
        }
        // Skip aria2c or other downloader progress lines
        if (trimmed.startsWith("[#") && lower.contains("eta")) {
            return false
        }
        // Skip ffmpeg progress lines
        if (lower.startsWith("frame=") || lower.startsWith("size=") || lower.contains("fps=")) {
            return false
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
