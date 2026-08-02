package com.shubh.anililitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.shubh.anililitv.data.auth.AccountService
import com.shubh.anililitv.data.library.LibraryStore
import com.shubh.anililitv.ui.adaptive.focusHighlight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TrackListMenu(
    entry: com.shubh.anililitv.data.library.WatchlistEntry,
    onDismiss: () -> Unit,
) {
    val statuses = listOf(
        "CURRENT" to "Watching",
        "PLANNING" to "Plan to watch",
        "COMPLETED" to "Completed",
        "REPEATING" to "Rewatching",
        "PAUSED" to "Paused",
        "DROPPED" to "Dropped"
    )

    var readyToAcceptInput by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        readyToAcceptInput = true
    }

    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Card(
            modifier = Modifier.width(200.dp),
            shape = androidx.compose.ui.graphics.RectangleShape,
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Black)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                statuses.forEachIndexed { index, (statusKey, statusLabel) ->
                    Text(
                        text = statusLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                            .focusHighlight()
                            .onKeyEvent { event ->
                                if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                                    if (event.type == KeyEventType.KeyUp) {
                                        if (readyToAcceptInput) {
                                            scope.launch {
                                                LibraryStore.updateWatchlistStatus(entry, statusKey)
                                            }
                                            onDismiss()
                                        }
                                        return@onKeyEvent true
                                    } else if (event.type == KeyEventType.KeyDown) {
                                        return@onKeyEvent true
                                    }
                                }
                                false
                            }
                            .clickable {
                                if (readyToAcceptInput) {
                                    scope.launch {
                                        LibraryStore.updateWatchlistStatus(entry, statusKey)
                                    }
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
