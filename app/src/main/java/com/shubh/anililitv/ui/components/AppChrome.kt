package com.shubh.anililitv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp

val LocalAppChromeVisible = compositionLocalOf { true }

val LocalAppChromeBottomInset = compositionLocalOf { 0.dp }

@Composable
fun ScrollAwareTopBar(content: @Composable () -> Unit) {
    val visible = LocalAppChromeVisible.current
    val shift by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(220),
        label = "topBarShift",
    )
    Layout(content = content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val width = placeables.maxOfOrNull { it.width } ?: 0
        layout(width, height) {
            placeables.forEach { placeable ->
                placeable.placeWithLayer(0, 0) {
                    translationY = -height * shift
                }
            }
        }
    }
}
