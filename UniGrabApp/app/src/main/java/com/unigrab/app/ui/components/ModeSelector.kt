package com.unigrab.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unigrab.app.data.local.entity.DownloadMode
import com.unigrab.app.ui.theme.DarkSurfaceVariant
import com.unigrab.app.ui.theme.PrimaryCyan
import com.unigrab.app.ui.theme.TextSecondary

@Composable
fun ModeSelector(
    selectedMode: DownloadMode,
    onModeSelected: (DownloadMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(DownloadMode.AUDIO, DownloadMode.VIDEO)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            val isSelected = mode == selectedMode
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) PrimaryCyan else Color.Transparent,
                label = "bgColor"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.Black else TextSecondary,
                label = "textColor"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .clickable { onModeSelected(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (mode == DownloadMode.AUDIO) "Audio" else "Video",
                    color = textColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
