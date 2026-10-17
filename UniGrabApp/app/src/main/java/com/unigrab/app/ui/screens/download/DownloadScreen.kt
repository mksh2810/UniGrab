package com.unigrab.app.ui.screens.download

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import android.widget.Toast
import androidx.compose.ui.text.style.TextOverflow
import com.unigrab.app.ui.components.formatDuration
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.unigrab.app.data.local.entity.DownloadMode
import com.unigrab.app.ui.components.AuraBackground
import com.unigrab.app.ui.components.GlassCard
import com.unigrab.app.ui.components.GlassDropdown
import com.unigrab.app.ui.components.GradientButton
import com.unigrab.app.ui.components.ModeSelector
import com.unigrab.app.ui.components.TrackListItem
import com.unigrab.app.ui.theme.ErrorRed
import com.unigrab.app.ui.theme.GlassBorderColor
import com.unigrab.app.ui.theme.GlassColor
import com.unigrab.app.ui.theme.PrimaryCyan
import com.unigrab.app.ui.theme.TextPrimary
import com.unigrab.app.ui.theme.TextSecondary

@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val url by viewModel.url.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val mediaInfo by viewModel.mediaInfo.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val qualityPreset by viewModel.qualityPreset.collectAsState()
    val convertToMp3 by viewModel.convertToMp3.collectAsState()
    val useAria2c by viewModel.useAria2c.collectAsState()
    val embedThumbnail by viewModel.embedThumbnail.collectAsState()
    val geoBypass by viewModel.geoBypass.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val threads by viewModel.threads.collectAsState()

    val scrollState = rememberScrollState()
    var showAgeGateDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var onConfirmAction by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null) }

    val handleUrlAction = { action: () -> Unit ->
        val isAdult = url.lowercase().let { urlLower ->
            listOf("pornhub", "xvideos", "xnxx", "redtube", "pornhat", "rule34", "youporn", "hqporner", "brazzers", "chaturbate", "onlyfans", "spankbang")
                .any { urlLower.contains(it) }
        }
        if (isAdult) {
            onConfirmAction = action
            showAgeGateDialog = true
        } else {
            action()
        }
    }

    val qualityPresets = listOf(
        "best" to "Best Quality",
        "1080p" to "1080p Max",
        "720p" to "720p Max",
        "480p" to "480p Max"
    )

    AuraBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 80.dp), // avoid bottom navigation bar overlap
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "UniGrab",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Black
            )

            Text(
                text = "Fast, client-side media downloader",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Input Card containing Mode Selector and URL input
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ModeSelector(
                        selectedMode = selectedMode,
                        onModeSelected = { viewModel.onModeChanged(it) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { viewModel.onUrlChanged(it) },
                            placeholder = { Text("Paste video or audio link...", color = TextSecondary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "Link",
                                    tint = PrimaryCyan
                                )
                            },
                            trailingIcon = {
                                if (url.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch() }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = TextSecondary
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .border(0.dp, Color.Transparent),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = PrimaryCyan
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    handleUrlAction {
                                        viewModel.searchUrl(context)
                                    }
                                    keyboardController?.hide()
                                }
                            )
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = {
                                handleUrlAction {
                                    viewModel.searchUrl(context)
                                }
                                keyboardController?.hide()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PrimaryCyan)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Loading / Error states
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(32.dp),
                    color = PrimaryCyan
                )
            }

            AnimatedVisibility(visible = error != null) {
                error?.let { errText ->
                    val clipboardManager = LocalClipboardManager.current
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Grab Error",
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(buildAnnotatedString { append(errText) })
                                        Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Error",
                                        tint = PrimaryCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = errText,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Results options
            AnimatedVisibility(visible = mediaInfo != null) {
                mediaInfo?.let { info ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TrackListItem(mediaInfo = info)

                        if (!info.playlistEntries.isNullOrEmpty()) {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Playlist Tracks (${info.playlistEntries.size})",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    info.playlistEntries.forEachIndexed { index, entry ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${index + 1}.",
                                                color = PrimaryCyan,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(28.dp)
                                            )
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = entry.title,
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!entry.uploader.isNullOrBlank()) {
                                                    Text(
                                                        text = entry.uploader,
                                                        color = TextSecondary,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = formatDuration(entry.durationSeconds),
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Simplified format selections
                        val audioOptions = listOf(
                            "mp3" to "MP3 (Default)",
                            "flac" to "Best Audio (FLAC)",
                            "m4a" to "M4A",
                            "aac" to "AAC",
                            "wav" to "WAV",
                            "opus" to "OPUS",
                            "ogg" to "OGG"
                        )

                        val videoOptions = listOf(
                            null to "Best Video + Best Audio (Default)",
                            "mp4" to "MP4 Container",
                            "webm" to "WebM Container",
                            "mkv" to "MKV Container"
                        )

                        GlassCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Download Settings",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )

                                if (selectedMode == DownloadMode.AUDIO) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Audio Format",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        GlassDropdown(
                                            options = audioOptions,
                                            selectedOption = audioOptions.find { it.first == selectedFormat } ?: audioOptions.first(),
                                            onOptionSelected = { viewModel.onFormatChanged(it.first) },
                                            labelProvider = { it.second }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Convert to MP3",
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Post-process audio to MP3 format",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Switch(
                                            checked = convertToMp3,
                                            onCheckedChange = { viewModel.onConvertToMp3Changed(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = PrimaryCyan,
                                                checkedTrackColor = Color(0x1F00F0FF)
                                            )
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Video Format",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        GlassDropdown(
                                            options = videoOptions,
                                            selectedOption = videoOptions.find { it.first == selectedFormat } ?: videoOptions.first(),
                                            onOptionSelected = { viewModel.onFormatChanged(it.first) },
                                            labelProvider = { it.second }
                                        )

                                        if (selectedFormat == null) {
                                            Text(
                                                text = "Resolution Preset",
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                            GlassDropdown(
                                                options = qualityPresets,
                                                selectedOption = qualityPresets.first { it.first == qualityPreset },
                                                onOptionSelected = { viewModel.onPresetChanged(it.first) },
                                                labelProvider = { it.second }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Accelerated Download (Aria2c)",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Use multi-threaded downloads",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Switch(
                                        checked = useAria2c,
                                        onCheckedChange = { viewModel.onUseAria2cChanged(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = PrimaryCyan,
                                            checkedTrackColor = Color(0x1F00F0FF)
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Embed Album Cover / Thumbnail",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Save media metadata cover art to file",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Switch(
                                        checked = embedThumbnail,
                                        onCheckedChange = { viewModel.onEmbedThumbnailChanged(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = PrimaryCyan,
                                            checkedTrackColor = Color(0x1F00F0FF)
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Bypass Geo Restrictions",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Attempt bypass of geographic locks",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Switch(
                                        checked = geoBypass,
                                        onCheckedChange = { viewModel.onGeoBypassChanged(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = PrimaryCyan,
                                            checkedTrackColor = Color(0x1F00F0FF)
                                        )
                                    )
                                }

                                if (selectedMode == DownloadMode.VIDEO) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Download Subtitles",
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Download all available subtitles for video",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Switch(
                                            checked = subtitles,
                                            onCheckedChange = { viewModel.onSubtitlesChanged(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = PrimaryCyan,
                                                checkedTrackColor = Color(0x1F00F0FF)
                                            )
                                        )
                                    }
                                }

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Concurrent Download Threads",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "$threads Threads",
                                            color = PrimaryCyan,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Slider(
                                        value = threads.toFloat(),
                                        onValueChange = { viewModel.onThreadsChanged(it.toInt()) },
                                        valueRange = 1f..8f,
                                        steps = 6,
                                        colors = SliderDefaults.colors(
                                            thumbColor = PrimaryCyan,
                                            activeTrackColor = PrimaryCyan,
                                            inactiveTrackColor = Color(0x1F00F0FF)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                 GradientButton(
                                    onClick = {
                                        handleUrlAction {
                                            viewModel.startDownload(context)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Grab Media")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAgeGateDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAgeGateDialog = false },
            title = {
                Text(
                    text = "18+ Age Confirmation",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This link appears to be from an adult website. By proceeding, you confirm that you are 18 years or older.\n\nNote: If this website is restricted in your region, please ensure you are connected to a working VPN to download.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showAgeGateDialog = false
                        onConfirmAction?.invoke()
                    }
                ) {
                    Text("Confirm", color = PrimaryCyan)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showAgeGateDialog = false }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = GlassColor,
            modifier = Modifier.border(1.dp, GlassBorderColor, RoundedCornerShape(16.dp))
        )
    }
}
