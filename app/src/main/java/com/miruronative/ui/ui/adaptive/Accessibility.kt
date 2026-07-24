package com.miruronative.ui.adaptive

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * True while a spoken-feedback screen reader (TalkBack) is running. Screen readers consume the
 * D-pad for accessibility navigation, so key-event-driven UI (like the TV player's "press any
 * key to show controls") must offer semantic click actions and stop auto-hiding while one is on.
 */
@Composable
fun rememberScreenReaderActive(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }

    fun screenReaderRunning(): Boolean = manager != null && manager.isEnabled &&
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
            .isNotEmpty()

    var active by remember { mutableStateOf(screenReaderRunning()) }
    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose {}
        val listener = AccessibilityManager.AccessibilityStateChangeListener {
            active = screenReaderRunning()
        }
        manager.addAccessibilityStateChangeListener(listener)
        active = screenReaderRunning()
        onDispose { manager.removeAccessibilityStateChangeListener(listener) }
    }
    return active
}
