package com.unigrab.app

import android.app.Application
import android.util.Log
import com.unigrab.app.data.download.DownloadManager
import com.unigrab.app.data.local.AppDatabase
import com.unigrab.app.data.repository.UniGrabRepository
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UniGrabApplication : Application() {

    lateinit var database: AppDatabase
    lateinit var repository: UniGrabRepository
    lateinit var downloadManager: DownloadManager

    override fun onCreate() {
        super.onCreate()

        // Initialize Local Storage Layer
        database = AppDatabase.getInstance(this)
        repository = UniGrabRepository(
            downloadTaskDao = database.downloadTaskDao(),
            libraryDao = database.libraryDao(),
            logDao = database.logDao()
        )
        downloadManager = DownloadManager(repository)

        // Initialize Native youtubedl-android components
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Aria2c.getInstance().init(this)
            Log.d("UniGrab", "Successfully initialized youtubedl-android engine components")
            checkForYtDlpUpdates()
        } catch (e: Exception) {
            Log.e("UniGrab", "Failed to initialize youtubedl-android native components", e)
        }
    }

    private fun checkForYtDlpUpdates() {
        val prefs = getSharedPreferences("unigrab_prefs", MODE_PRIVATE)
        val lastUpdate = prefs.getLong("last_ytdlp_update", 0L)
        val currentTime = System.currentTimeMillis()
        val oneWeekMs = 7L * 24L * 60L * 60L * 1000L

        if (lastUpdate == 0L || (currentTime - lastUpdate) > oneWeekMs) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("UniGrab", "Starting background update of yt-dlp...")
                    val status = YoutubeDL.getInstance().updateYoutubeDL(this@UniGrabApplication)
                    Log.d("UniGrab", "yt-dlp update finished with status: $status")
                    prefs.edit().putLong("last_ytdlp_update", System.currentTimeMillis()).apply()
                } catch (e: Exception) {
                    Log.e("UniGrab", "Failed to auto-update yt-dlp", e)
                }
            }
        }
    }
}
