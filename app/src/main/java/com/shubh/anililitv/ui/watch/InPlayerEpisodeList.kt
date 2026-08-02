package com.shubh.anililitv.ui.watch

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shubh.anililitv.data.model.EpisodeItem
import com.shubh.anililitv.ui.adaptive.focusHighlight
import com.shubh.anililitv.ui.components.EpisodeBlockPicker
import com.shubh.anililitv.ui.components.EqualizerWaveIndicator
import com.shubh.anililitv.ui.components.FastScrollbar
import com.shubh.anililitv.ui.components.blockIndexContaining
import com.shubh.anililitv.ui.components.episodeBlocks

@Composable
internal fun InPlayerEpisodeDrawer(
    episodes: List<EpisodeItem>,
    currentIndex: Int,
    artworkUrl: String?,
    onSelectEpisode: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    var chosenBlockIndex by remember(episodes) { mutableStateOf<Int?>(null) }
    val blocks = remember(episodes) { episodeBlocks(episodes, blockSize = 30) }
    val currentEpisode = episodes.getOrNull(currentIndex)
    val defaultBlockIndex = remember(blocks, currentEpisode) {
        blockIndexContaining(blocks, currentEpisode?.number).coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
    }
    val activeBlockIndex = chosenBlockIndex ?: defaultBlockIndex

    val listState = rememberTvLazyGridState()
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(activeBlockIndex, currentIndex) {
        val shownEpisodes = blocks.getOrNull(activeBlockIndex)?.episodes ?: episodes
        val targetInShown = shownEpisodes.indexOfFirst { it.pipeId == currentEpisode?.pipeId }
        if (targetInShown >= 0) {
            listState.scrollToItem(targetInShown)
        }
        runCatching { initialFocusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(360.dp)
                .clickable(enabled = false) {},
            color = Color(0xF012131A),
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .focusGroup(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.focusHighlight(RoundedCornerShape(20.dp)),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close episodes",
                            tint = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    val shownEpisodes = remember(blocks, activeBlockIndex, currentEpisode) {
                        val currentBlock = blocks.getOrNull(activeBlockIndex)?.episodes ?: episodes
                        val isLastInBlock = currentEpisode != null && currentBlock.lastOrNull()?.pipeId == currentEpisode.pipeId
                        if (isLastInBlock && activeBlockIndex + 1 < blocks.size) {
                            currentBlock + blocks[activeBlockIndex + 1].episodes
                        } else {
                            currentBlock
                        }
                    }
                    TvLazyVerticalGrid(
                        columns = TvGridCells.Adaptive(60.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(shownEpisodes, key = { it.pipeId }, contentType = { "episode" }) { episode ->
                            val isCurrent = episode.pipeId == currentEpisode?.pipeId
                            val defaultFocusRequester = remember { FocusRequester() }
                            val itemFocusRequester = if (isCurrent) initialFocusRequester else defaultFocusRequester

                            InPlayerEpisodeItemRow(
                                episode = episode,
                                artworkUrl = artworkUrl,
                                isCurrent = isCurrent,
                                focusRequester = itemFocusRequester,
                                onClick = {
                                    val globalIndex = episodes.indexOf(episode)
                                    if (globalIndex >= 0) {
                                        onSelectEpisode(globalIndex)
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN) {
                                        episode == shownEpisodes.last()
                                    } else false
                                }
                            )
                        }
                    }


                }
            }
        }
    }
}

@Composable
private fun SeasonHeaderRow(
    seasonNumber: Int,
    rangeLabel: String,
    episodeCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .focusHighlight(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.07f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Season $seasonNumber ($rangeLabel)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "$episodeCount episodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse season" else "Expand season",
                tint = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun InPlayerEpisodeItemRow(
    episode: EpisodeItem,
    artworkUrl: String?,
    isCurrent: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isCurrent) {
        Color(0xFF333333)
    } else {
        Color(0xFF1E1F2B)
    }
    val borderColor = if (isCurrent) Color.White else Color.Transparent

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                val activate = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                    keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                if (!activate) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                }
            }
            .focusHighlight(RoundedCornerShape(8.dp), focusedScale = 1.05f)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = episode.displayNumber,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.7f),
        )
    }
}
