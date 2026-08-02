package com.shubh.anililitv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BAR_DURATIONS_MS = intArrayOf(380, 510, 440, 610)
private val BAR_PEAKS = floatArrayOf(0.95f, 0.55f, 0.80f, 0.45f)

@Composable
fun EqualizerWaveIndicator(
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    color: Color = MaterialTheme.colorScheme.primary,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 16.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer_transition")

    val animValues = List(barCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = BAR_PEAKS[index % BAR_PEAKS.size],
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = BAR_DURATIONS_MS[index % BAR_DURATIONS_MS.size],
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar_anim_$index",
        )
    }

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        animValues.forEach { anim ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(anim.value)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color),
            )
        }
    }
}
