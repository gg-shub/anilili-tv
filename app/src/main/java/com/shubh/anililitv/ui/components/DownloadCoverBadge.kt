package com.shubh.anililitv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shubh.anililitv.playback.EpisodeDownloadStage
import com.shubh.anililitv.playback.EpisodeDownloadUi

@Composable
fun DownloadCoverBadge(
    state: EpisodeDownloadUi?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
        modifier = modifier,
    ) {
        val current = state ?: return@AnimatedVisibility
        when (current.stage) {
            EpisodeDownloadStage.SAVED -> CornerMark(
                color = MaterialTheme.colorScheme.primary,
                compact = compact,
                saved = true,
            )
            EpisodeDownloadStage.FAILED -> CornerMark(
                color = MaterialTheme.colorScheme.error,
                compact = compact,
                saved = false,
            )
            else -> ActiveOverlay(current, compact)
        }
    }
}

@Composable
private fun ActiveOverlay(state: EpisodeDownloadUi, compact: Boolean) {
    val ringSize = if (compact) 30.dp else 42.dp
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = state.progress,
                    modifier = Modifier.size(ringSize),
                    color = MaterialTheme.colorScheme.primary,
                )
                state.progress?.let {
                    Text(
                        "${(it * 100).toInt()}",
                        style = if (compact) {
                            MaterialTheme.typography.labelSmall
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            if (!compact) {
                Text(
                    when (state.stage) {
                        EpisodeDownloadStage.QUEUED -> "Queued"
                        EpisodeDownloadStage.CONVERTING -> "Converting"
                        else -> "Downloading"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.86f),
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(
    progress: Float?,
    modifier: Modifier,
    color: Color,
) {
    val sweep by animateFloatAsState(
        targetValue = (progress ?: 0f) * 360f,
        animationSpec = tween(400, easing = LinearEasing),
        label = "downloadSweep",
    )
    val spin by rememberInfiniteTransition(label = "downloadSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "downloadSpinAngle",
    )
    val track = Color.White.copy(alpha = 0.28f)
    Canvas(modifier) {
        val stroke = size.minDimension * 0.11f
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = if (progress == null) spin - 90f else -90f,
            sweepAngle = if (progress == null) 90f else sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun CornerMark(color: Color, compact: Boolean, saved: Boolean) {
    Box(Modifier.fillMaxSize()) {
        Icon(
            imageVector = if (saved) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = if (saved) "Saved offline" else "Download failed",
            tint = color,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
                .size(if (compact) 14.dp else 18.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        )
    }
}
