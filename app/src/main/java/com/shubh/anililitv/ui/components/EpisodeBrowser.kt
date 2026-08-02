package com.shubh.anililitv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shubh.anililitv.data.model.EpisodeItem
import com.shubh.anililitv.data.settings.EpisodeLayout
import com.shubh.anililitv.ui.adaptive.LocalAppDeviceProfile
import com.shubh.anililitv.ui.adaptive.TvNativeTextField
import com.shubh.anililitv.ui.adaptive.focusHighlight

const val EPISODE_BLOCK_SIZE = 30

const val EPISODE_BROWSER_MIN_EPISODES = 12

data class EpisodeBlock(val label: String, val episodes: List<EpisodeItem>)

fun episodeBlocks(
    episodes: List<EpisodeItem>,
    blockSize: Int = EPISODE_BLOCK_SIZE,
): List<EpisodeBlock> = episodes.chunked(blockSize).map { chunk ->
    val first = chunk.first().displayNumber
    val last = chunk.last().displayNumber
    EpisodeBlock(if (first == last) first else "$first – $last", chunk)
}

fun episodeMatchesQuery(episode: EpisodeItem, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    if (episode.displayNumber.startsWith(trimmed, ignoreCase = true)) return true
    return episode.distinctTitle?.contains(trimmed, ignoreCase = true) == true
}

fun filterEpisodes(episodes: List<EpisodeItem>, query: String): List<EpisodeItem> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return episodes
    val (byNumber, byTitle) = episodes
        .filter { episodeMatchesQuery(it, trimmed) }
        .partition { it.displayNumber.startsWith(trimmed, ignoreCase = true) }
    return byNumber + byTitle
}

fun blockIndexContaining(blocks: List<EpisodeBlock>, number: Double?): Int {
    if (number == null) return 0
    return blocks.indexOfFirst { block -> block.episodes.any { it.number == number } }
        .coerceAtLeast(0)
}

@Composable
fun EpisodeBrowserBar(
    blocks: List<EpisodeBlock>,
    selectedBlockIndex: Int,
    onSelectBlock: (Int) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    layout: EpisodeLayout,
    onToggleLayout: () -> Unit,
    modifier: Modifier = Modifier,
    showLayoutToggle: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (blocks.size > 1) {
            EpisodeBlockPicker(
                blocks = blocks,
                selectedIndex = selectedBlockIndex,
                onSelect = onSelectBlock,
            )
        }
        EpisodeFilterField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.weight(1f),
        )
        if (showLayoutToggle) EpisodeLayoutToggle(layout = layout, onToggle = onToggleLayout)
    }
}

@Composable
internal fun EpisodeBlockPicker(
    blocks: List<EpisodeBlock>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = blocks.getOrNull(selectedIndex) ?: blocks.first()
    Box {
        Row(
            modifier = Modifier
                .focusHighlight(RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .clickable(onClickLabel = "Choose episode range") { expanded = true }
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            blocks.forEachIndexed { index, block ->
                DropdownMenuItem(
                    text = { Text(block.label) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                    trailingIcon = if (index == selectedIndex) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun EpisodeFilterField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = LocalAppDeviceProfile.current
    if (device.isTv) {
        TvNativeTextField(
            value = query,
            onValueChange = onQueryChange,
            hint = "Filter episodes",
            modifier = modifier,
        )
    } else {
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val colors = MaterialTheme.colorScheme
        val textStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onSurface)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = modifier,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                        .padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.onSurfaceVariant,
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                "Filter episodes…",
                                style = textStyle.copy(color = colors.onSurfaceVariant),
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear filter",
                                modifier = Modifier.size(20.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun EpisodeLayoutToggle(layout: EpisodeLayout, onToggle: () -> Unit) {
    val showsGridNext = layout == EpisodeLayout.LIST
    IconButton(
        onClick = onToggle,
        modifier = Modifier.focusHighlight(RoundedCornerShape(10.dp)),
    ) {
        Icon(
            imageVector = if (showsGridNext) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
            contentDescription = if (showsGridNext) "Show as grid" else "Show as list",
        )
    }
}

@Composable
fun EpisodeNumberChip(
    episode: EpisodeItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    watchedFraction: Float = 0f,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        episode.filler -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        modifier = modifier
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
            .focusHighlight(RoundedCornerShape(8.dp))
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            episode.displayNumber,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            EqualizerWaveIndicator(
                barCount = 3,
                color = MaterialTheme.colorScheme.onPrimary,
                barWidth = 2.dp,
                maxHeight = 10.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 5.dp),
            )
        } else if (episode.filler) {
            Text(
                "F",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp),
            )
        }
        if (!selected) {
            WatchProgressBar(
                fraction = watchedFraction,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
