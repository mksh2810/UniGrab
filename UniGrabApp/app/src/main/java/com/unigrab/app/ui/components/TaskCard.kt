package com.unigrab.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unigrab.app.data.local.entity.DownloadStatus
import com.unigrab.app.data.local.entity.DownloadTask
import com.unigrab.app.ui.theme.ErrorRed
import com.unigrab.app.ui.theme.PrimaryCyan
import com.unigrab.app.ui.theme.SuccessGreen
import com.unigrab.app.ui.theme.TextPrimary
import com.unigrab.app.ui.theme.TextSecondary
import com.unigrab.app.ui.theme.WarningYellow

@Composable
fun TaskCard(
    task: DownloadTask,
    onCancelClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onLogsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = task.status == DownloadStatus.DOWNLOADING ||
            task.status == DownloadStatus.EXTRACTING ||
            task.status == DownloadStatus.MERGING ||
            task.status == DownloadStatus.PENDING

    val statusColor = when (task.status) {
        DownloadStatus.COMPLETED -> SuccessGreen
        DownloadStatus.FAILED -> ErrorRed
        DownloadStatus.CANCELLED -> TextSecondary
        DownloadStatus.DOWNLOADING -> PrimaryCyan
        else -> WarningYellow
    }

    GlassCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = task.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = task.status.name,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                IconButton(onClick = onLogsClick) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Logs",
                        tint = TextSecondary
                    )
                }

                if (isActive) {
                    IconButton(onClick = onPauseClick) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = WarningYellow
                        )
                    }
                    IconButton(onClick = onCancelClick) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Cancel",
                            tint = ErrorRed
                        )
                    }
                } else if (task.status == DownloadStatus.PAUSED) {
                    IconButton(onClick = onResumeClick) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = SuccessGreen
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = TextSecondary
                        )
                    }
                } else {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = TextSecondary
                        )
                    }
                }
            }

            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.MERGING) {
                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = PrimaryCyan,
                    trackColor = Color(0x1F00F0FF)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${task.progress.toInt()}%",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${task.speed} • ETA ${task.eta}",
                        color = TextSecondary
                    )
                }
            } else if (task.status == DownloadStatus.FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                val context = LocalContext.current
                val clipboardManager = LocalClipboardManager.current
                val errorText = task.error ?: "Unknown error occurred"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = errorText,
                        color = ErrorRed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.setText(buildAnnotatedString { append(errorText) })
                            Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Error",
                            tint = ErrorRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
