package com.miruronative.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

const val LaunchSplashFadeMillis = 350
private const val LaunchSplashHoldMillis = 450

@Composable
fun AppLaunchSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        delay((WaterFillDurationMillis + LaunchSplashHoldMillis).toLong())
        latestOnFinished()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val logoSize = when {
            maxWidth >= 800.dp -> 200.dp
            maxWidth >= 500.dp -> 160.dp
            else -> 130.dp
        }
        WaterFillLogoIndicator(
            size = logoSize,
            playOnce = true,
        )
    }
}
