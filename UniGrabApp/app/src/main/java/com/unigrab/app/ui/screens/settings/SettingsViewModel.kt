package com.unigrab.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus: StateFlow<String?> = _updateStatus.asStateFlow()

    private val _ytDlpVersion = MutableStateFlow("Loading...")
    val ytDlpVersion: StateFlow<String> = _ytDlpVersion.asStateFlow()

    private val _downloadDirectory = MutableStateFlow("")
    val downloadDirectory: StateFlow<String> = _downloadDirectory.asStateFlow()

    private val _cookiesPath = MutableStateFlow("")
    val cookiesPath: StateFlow<String> = _cookiesPath.asStateFlow()

    private val _proxyUrl = MutableStateFlow("")
    val proxyUrl: StateFlow<String> = _proxyUrl.asStateFlow()

    private val _defaultAudioFormat = MutableStateFlow("bestaudio")
    val defaultAudioFormat: StateFlow<String> = _defaultAudioFormat.asStateFlow()

    private val _defaultAudioQuality = MutableStateFlow("best")
    val defaultAudioQuality: StateFlow<String> = _defaultAudioQuality.asStateFlow()

    private val _defaultVideoFormat = MutableStateFlow("bestvideo+bestaudio/best")
    val defaultVideoFormat: StateFlow<String> = _defaultVideoFormat.asStateFlow()

    private val _defaultVideoQuality = MutableStateFlow("best")
    val defaultVideoQuality: StateFlow<String> = _defaultVideoQuality.asStateFlow()

    fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences("unigrab_prefs", Context.MODE_PRIVATE)
        _downloadDirectory.value = prefs.getString("download_directory", "") ?: ""
        _cookiesPath.value = prefs.getString("cookies_path", "") ?: ""
        _proxyUrl.value = prefs.getString("proxy_url", "") ?: ""
        _defaultAudioFormat.value = prefs.getString("default_audio_format", "bestaudio") ?: "bestaudio"
        _defaultAudioQuality.value = prefs.getString("default_audio_quality", "best") ?: "best"
        _defaultVideoFormat.value = prefs.getString("default_video_format", "bestvideo+bestaudio/best") ?: "bestvideo+bestaudio/best"
        _defaultVideoQuality.value = prefs.getString("default_video_quality", "best") ?: "best"
    }

    fun saveSettings(
        context: Context,
        downloadDirectory: String,
        cookiesPath: String,
        proxyUrl: String,
        defaultAudioFormat: String,
        defaultAudioQuality: String,
        defaultVideoFormat: String,
        defaultVideoQuality: String
    ) {
        val prefs = context.getSharedPreferences("unigrab_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("download_directory", downloadDirectory)
            putString("cookies_path", cookiesPath)
            putString("proxy_url", proxyUrl)
            putString("default_audio_format", defaultAudioFormat)
            putString("default_audio_quality", defaultAudioQuality)
            putString("default_video_format", defaultVideoFormat)
            putString("default_video_quality", defaultVideoQuality)
            apply()
        }
        _downloadDirectory.value = downloadDirectory
        _cookiesPath.value = cookiesPath
        _proxyUrl.value = proxyUrl
        _defaultAudioFormat.value = defaultAudioFormat
        _defaultAudioQuality.value = defaultAudioQuality
        _defaultVideoFormat.value = defaultVideoFormat
        _defaultVideoQuality.value = defaultVideoQuality
    }

    fun loadYtDlpVersion(context: Context) {
        viewModelScope.launch {
            try {
                val version = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().version(context) ?: "Unknown"
                }
                _ytDlpVersion.value = version
            } catch (e: Exception) {
                _ytDlpVersion.value = "Not initialized"
            }
        }
    }

    fun updateYtDlp(context: Context) {
        viewModelScope.launch {
            _isUpdating.value = true
            _updateStatus.value = "Checking for updates..."
            try {
                val status = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                }
                _updateStatus.value = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> {
                        loadYtDlpVersion(context)
                        "Updated successfully!"
                    }
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Already up to date."
                    else -> "Check complete."
                }
            } catch (e: Exception) {
                _updateStatus.value = "Update failed: ${e.localizedMessage ?: e.message}"
            } finally {
                _isUpdating.value = false
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel() as T
        }
    }
}
