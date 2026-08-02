package com.shubh.anililitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val THUMB_HEIGHT = 44.dp
private val TRACK_WIDTH = 8.dp

fun calculateScrollTargetIndex(fraction: Float, totalItems: Int): Int {
    if (totalItems <= 0) return 0
    val clamped = fraction.coerceIn(0f, 1f)
    return ((totalItems - 1) * clamped).roundToInt().coerceIn(0, totalItems - 1)
}

internal fun thumbOffsetPx(trackHeightPx: Float, thumbHeightPx: Float, progressFraction: Float): Float =
    (trackHeightPx - thumbHeightPx).coerceAtLeast(0f) * progressFraction.coerceIn(0f, 1f)

@Composable
fun FastScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.16f),
    thumbColor: Color = MaterialTheme.colorScheme.primary,
) {
    val coroutineScope = rememberCoroutineScope()
    val totalItems = state.layoutInfo.totalItemsCount

    if (totalItems <= 0) return

    val lastScrollableIndex = (totalItems - 1).coerceAtLeast(1)
    val progressFraction =
        (state.firstVisibleItemIndex.toFloat() / lastScrollableIndex.toFloat()).coerceIn(0f, 1f)

    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    val thumbHeightPx = with(LocalDensity.current) { THUMB_HEIGHT.toPx() }

    val scrollJob = remember { mutableStateOf<Job?>(null) }
    fun scrollTo(offsetY: Float) {
        val fraction = (offsetY / trackHeightPx).coerceIn(0f, 1f)
        val target = calculateScrollTargetIndex(fraction, totalItems)
        scrollJob.value?.cancel()
        scrollJob.value = coroutineScope.launch { state.scrollToItem(target) }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(16.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(TRACK_WIDTH)
                .onGloballyPositioned { trackHeightPx = it.size.height.toFloat().coerceAtLeast(1f) }
                .clip(RoundedCornerShape(4.dp))
                .background(trackColor)
                .pointerInput(totalItems) {
                    detectTapGestures { offset -> scrollTo(offset.y) }
                }
                .pointerInput(totalItems) {
                    var dragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragY = offset.y
                            scrollTo(dragY)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragY = (dragY + dragAmount).coerceIn(0f, trackHeightPx)
                            scrollTo(dragY)
                        },
                    )
                },
        ) {
            val thumbOffsetYPx = thumbOffsetPx(trackHeightPx, thumbHeightPx, progressFraction)

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = thumbOffsetYPx.roundToInt()) }
                    .width(TRACK_WIDTH)
                    .height(THUMB_HEIGHT)
                    .clip(RoundedCornerShape(4.dp))
                    .background(thumbColor),
            )
        }
    }
}
