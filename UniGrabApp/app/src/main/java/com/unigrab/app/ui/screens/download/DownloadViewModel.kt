package com.unigrab.app.ui.screens.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.unigrab.app.data.download.DownloadManager
import com.unigrab.app.data.local.entity.DownloadMode
import com.unigrab.app.data.model.DownloadOptions
import com.unigrab.app.data.model.MediaFormat
import com.unigrab.app.data.model.MediaInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadViewModel(
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _mediaInfo = MutableStateFlow<MediaInfo?>(null)
    val mediaInfo: StateFlow<MediaInfo?> = _mediaInfo.asStateFlow()

    private val _selectedMode = MutableStateFlow(DownloadMode.VIDEO)
    val selectedMode: StateFlow<DownloadMode> = _selectedMode.asStateFlow()

    private val _selectedFormat = MutableStateFlow<String?>(null)
    val selectedFormat: StateFlow<String?> = _selectedFormat.asStateFlow()

    private val _qualityPreset = MutableStateFlow("best")
    val qualityPreset: StateFlow<String> = _qualityPreset.asStateFlow()

    private val _convertToMp3 = MutableStateFlow(false)
    val convertToMp3: StateFlow<Boolean> = _convertToMp3.asStateFlow()

    private val _useAria2c = MutableStateFlow(true)
    val useAria2c: StateFlow<Boolean> = _useAria2c.asStateFlow()

    private val _embedThumbnail = MutableStateFlow(true)
    val embedThumbnail: StateFlow<Boolean> = _embedThumbnail.asStateFlow()

    private val _geoBypass = MutableStateFlow(false)
    val geoBypass: StateFlow<Boolean> = _geoBypass.asStateFlow()

    private val _subtitles = MutableStateFlow(false)
    val subtitles: StateFlow<Boolean> = _subtitles.asStateFlow()

    private val _threads = MutableStateFlow(4)
    val threads: StateFlow<Int> = _threads.asStateFlow()

    fun onUrlChanged(newUrl: String) {
        _url.value = newUrl
        _error.value = null
    }

    fun onModeChanged(newMode: DownloadMode) {
        _selectedMode.value = newMode
        _selectedFormat.value = if (newMode == DownloadMode.AUDIO) "mp3" else null
    }

    fun onFormatChanged(newFormat: String?) {
        _selectedFormat.value = newFormat
    }

    fun onPresetChanged(newPreset: String) {
        _qualityPreset.value = newPreset
    }

    fun onConvertToMp3Changed(convert: Boolean) {
        _convertToMp3.value = convert
    }

    fun onUseAria2cChanged(use: Boolean) {
        _useAria2c.value = use
    }

    fun onEmbedThumbnailChanged(embed: Boolean) {
        _embedThumbnail.value = embed
    }

    fun onGeoBypassChanged(bypass: Boolean) {
        _geoBypass.value = bypass
    }

    fun onSubtitlesChanged(subs: Boolean) {
        _subtitles.value = subs
    }

    fun onThreadsChanged(count: Int) {
        _threads.value = count
    }

    fun searchUrl(context: Context) {
        val currentUrl = _url.value.trim()
        if (currentUrl.isEmpty()) {
            _error.value = "Please enter a valid URL"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _mediaInfo.value = null
            try {
                val prefs = context.getSharedPreferences("unigrab_prefs", Context.MODE_PRIVATE)
                val proxy = prefs.getString("proxy_url", null).takeIf { !it.isNullOrBlank() }
                val info = downloadManager.fetchMediaInfo(currentUrl, proxy)
                _mediaInfo.value = info
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: e.message ?: "Failed to resolve link info"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startDownload(context: Context) {
        val info = _mediaInfo.value ?: return
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("unigrab_prefs", Context.MODE_PRIVATE)
            val proxy = prefs.getString("proxy_url", null).takeIf { !it.isNullOrBlank() }
            val cookies = prefs.getString("cookies_path", null).takeIf { !it.isNullOrBlank() }

            val options = DownloadOptions(
                downloadMode = _selectedMode.value,
                selectedFormatId = _selectedFormat.value,
                qualityPreset = _qualityPreset.value,
                convertToMp3 = _selectedFormat.value == "mp3",
                useAria2c = _useAria2c.value,
                embedThumbnail = _embedThumbnail.value,
                geoBypass = _geoBypass.value,
                subtitles = _subtitles.value,
                threads = _threads.value,
                proxy = proxy,
                cookies = cookies
            )
            if (!info.playlistEntries.isNullOrEmpty()) {
                info.playlistEntries.forEach { entry ->
                    downloadManager.enqueueDownload(context, entry.url, entry.title, options)
                }
            } else {
                downloadManager.enqueueDownload(context, info.url, info.title, options)
            }
            clearSearch()
        }
    }

    fun clearSearch() {
        _url.value = ""
        _mediaInfo.value = null
        _error.value = null
        _selectedFormat.value = if (_selectedMode.value == DownloadMode.AUDIO) "mp3" else null
    }

    class Factory(private val downloadManager: DownloadManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DownloadViewModel(downloadManager) as T
        }
    }
}
