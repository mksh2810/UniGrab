package com.unigrab.app.ui.screens.settings

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unigrab.app.ui.components.AuraBackground
import com.unigrab.app.ui.components.GlassCard
import com.unigrab.app.ui.components.GlassDropdown
import com.unigrab.app.ui.components.GradientButton
import com.unigrab.app.ui.theme.GlassBorderColor
import com.unigrab.app.ui.theme.GlassColor
import com.unigrab.app.ui.theme.PrimaryCyan
import com.unigrab.app.ui.theme.TextPrimary
import com.unigrab.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUpdating by viewModel.isUpdating.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val ytDlpVersion by viewModel.ytDlpVersion.collectAsState()

    val savedDownloadDirectory by viewModel.downloadDirectory.collectAsState()
    val savedCookiesPath by viewModel.cookiesPath.collectAsState()
    val savedProxyUrl by viewModel.proxyUrl.collectAsState()
    val savedDefaultAudioFormat by viewModel.defaultAudioFormat.collectAsState()
    val savedDefaultAudioQuality by viewModel.defaultAudioQuality.collectAsState()
    val savedDefaultVideoFormat by viewModel.defaultVideoFormat.collectAsState()
    val savedDefaultVideoQuality by viewModel.defaultVideoQuality.collectAsState()

    var downloadDirectory by remember { mutableStateOf("") }
    var cookiesPath by remember { mutableStateOf("") }
    var proxyUrl by remember { mutableStateOf("") }
    var defaultAudioFormat by remember { mutableStateOf("bestaudio") }
    var defaultAudioQuality by remember { mutableStateOf("best") }
    var defaultVideoFormat by remember { mutableStateOf("bestvideo+bestaudio/best") }
    var defaultVideoQuality by remember { mutableStateOf("best") }

    val audioFormats = listOf(
        "bestaudio" to "Best Audio (Default)",
        "aac" to "AAC",
        "mp3" to "MP3",
        "m4a" to "M4A",
        "opus" to "Opus"
    )

    val audioQualities = listOf(
        "best" to "Best Quality (Default)",
        "320k" to "320 kbps (High)",
        "256k" to "256 kbps",
        "192k" to "192 kbps (Medium)",
        "128k" to "128 kbps (Low)"
    )

    val videoFormats = listOf(
        "bestvideo+bestaudio/best" to "Best Video + Best Audio (Default)",
        "mp4" to "MP4 Container",
        "webm" to "WebM Container",
        "mkv" to "MKV Container"
    )

    val videoQualities = listOf(
        "best" to "Best Quality (Default)",
        "1080p" to "1080p Max",
        "720p" to "720p Max",
        "480p" to "480p Max",
        "360p" to "360p Max"
    )

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.loadYtDlpVersion(context)
        viewModel.loadSettings(context)
    }

    LaunchedEffect(savedDownloadDirectory) { downloadDirectory = savedDownloadDirectory }
    LaunchedEffect(savedCookiesPath) { cookiesPath = savedCookiesPath }
    LaunchedEffect(savedProxyUrl) { proxyUrl = savedProxyUrl }
    LaunchedEffect(savedDefaultAudioFormat) { defaultAudioFormat = savedDefaultAudioFormat }
    LaunchedEffect(savedDefaultAudioQuality) { defaultAudioQuality = savedDefaultAudioQuality }
    LaunchedEffect(savedDefaultVideoFormat) { defaultVideoFormat = savedDefaultVideoFormat }
    LaunchedEffect(savedDefaultVideoQuality) { defaultVideoQuality = savedDefaultVideoQuality }

    val defaultDownloadDir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "UniGrab").absolutePath
    val displayStoragePath = downloadDirectory.ifBlank { defaultDownloadDir }

    AuraBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Settings",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Black
            )

            Text(
                text = "Configure application options",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Storage Section
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Storage",
                            tint = PrimaryCyan
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Storage Location",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Customize where files are saved. Leave empty to use application sandbox Downloads folder:",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsTextField(
                        value = downloadDirectory,
                        onValueChange = { downloadDirectory = it },
                        label = "Custom Download Path",
                        placeholder = defaultDownloadDir
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current Path: $displayStoragePath",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Network and Auth Settings
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Network",
                            tint = PrimaryCyan
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Network & Authentication",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsTextField(
                        value = proxyUrl,
                        onValueChange = { proxyUrl = it },
                        label = "Proxy Server URL",
                        placeholder = "e.g., http://127.0.0.1:8080 or socks5://user:pass@host:port"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsTextField(
                        value = cookiesPath,
                        onValueChange = { cookiesPath = it },
                        label = "Netscape Cookies File Path",
                        placeholder = "e.g., /sdcard/Download/cookies.txt"
                    )
                }
            }

            // Default Presets Settings
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Presets",
                            tint = PrimaryCyan
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Default Presets",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // Audio Presets
                    Text(
                        text = "Default Audio Format",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    GlassDropdown(
                        options = audioFormats,
                        selectedOption = audioFormats.firstOrNull { it.first == defaultAudioFormat } ?: audioFormats.first(),
                        onOptionSelected = { defaultAudioFormat = it.first },
                        labelProvider = { it.second }
                    )

                    Text(
                        text = "Default Audio Quality",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    GlassDropdown(
                        options = audioQualities,
                        selectedOption = audioQualities.firstOrNull { it.first == defaultAudioQuality } ?: audioQualities.first(),
                        onOptionSelected = { defaultAudioQuality = it.first },
                        labelProvider = { it.second }
                    )

                    // Video Presets
                    Text(
                        text = "Default Video Format",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    GlassDropdown(
                        options = videoFormats,
                        selectedOption = videoFormats.firstOrNull { it.first == defaultVideoFormat } ?: videoFormats.first(),
                        onOptionSelected = { defaultVideoFormat = it.first },
                        labelProvider = { it.second }
                    )

                    Text(
                        text = "Default Video Quality Preset",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    GlassDropdown(
                        options = videoQualities,
                        selectedOption = videoQualities.firstOrNull { it.first == defaultVideoQuality } ?: videoQualities.first(),
                        onOptionSelected = { defaultVideoQuality = it.first },
                        labelProvider = { it.second }
                    )
                }
            }

            // Save Button
            GradientButton(
                onClick = {
                    viewModel.saveSettings(
                        context = context,
                        downloadDirectory = downloadDirectory,
                        cookiesPath = cookiesPath,
                        proxyUrl = proxyUrl,
                        defaultAudioFormat = defaultAudioFormat,
                        defaultAudioQuality = defaultAudioQuality,
                        defaultVideoFormat = defaultVideoFormat,
                        defaultVideoQuality = defaultVideoQuality
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save settings"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Settings & Presets")
            }

            // Engine Section
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Engine",
                            tint = PrimaryCyan
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Download Engine",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "yt-dlp version",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = ytDlpVersion,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    GradientButton(
                        onClick = { viewModel.updateYtDlp(context) },
                        enabled = !isUpdating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Update"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update yt-dlp Engine")
                    }

                    updateStatus?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = PrimaryCyan,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // About Section
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            tint = PrimaryCyan
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "About UniGrab",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "UniGrab is an open source, fully client-side downloader. No servers are used to parse links or download media.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "License: GPL-3.0 (Trusted Open Source)",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Privacy: 100% Offline & Private",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Version: 1.0.0 (Release Build)",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextSecondary) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GlassBorderColor, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(GlassColor),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = PrimaryCyan
            )
        )
    }
}
