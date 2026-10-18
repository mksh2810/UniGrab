package com.unigrab.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.unigrab.app.ui.theme.AuraCyan
import com.unigrab.app.ui.theme.AuraPurple
import com.unigrab.app.ui.theme.DarkBackground

@Composable
fun AuraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aura")

    val xOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "xOffset1"
    )

    val yOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yOffset1"
    )

    val xOffset2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "xOffset2"
    )

    val yOffset2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yOffset2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Blob 1: Cyan aura
            val center1 = Offset(
                x = width * (0.2f + 0.6f * xOffset1),
                y = height * (0.2f + 0.6f * yOffset1)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AuraCyan, AuraCyan.copy(alpha = 0f)),
                    center = center1,
                    radius = width * 0.7f
                ),
                center = center1,
                radius = width * 0.7f
            )

            // Blob 2: Purple aura
            val center2 = Offset(
                x = width * (0.1f + 0.8f * xOffset2),
                y = height * (0.1f + 0.8f * yOffset2)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AuraPurple, AuraPurple.copy(alpha = 0f)),
                    center = center2,
                    radius = width * 0.7f
                ),
                center = center2,
                radius = width * 0.7f
            )
        }

        content()
    }
}
