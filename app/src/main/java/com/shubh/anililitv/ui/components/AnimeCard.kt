package com.shubh.anililitv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shubh.anililitv.data.model.Media
import com.shubh.anililitv.ui.adaptive.LocalAppDeviceProfile
import com.shubh.anililitv.ui.adaptive.focusHighlight

@Composable
fun AnimeCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = LocalAppDeviceProfile.current

    val titleStyle = if (device.isTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelLarge
    val titleMaxLines = if (device.isTv) 2 else 1
    val tvTitleMinHeight = with(LocalDensity.current) {
        titleStyle.lineHeight.toDp() * titleMaxLines + 4.dp
    }
    val posterSize by com.shubh.anililitv.data.settings.SettingsStore.posterSize.collectAsState()
    val cardWidth = posterSize.widthDp.dp
    var isFocused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .width(cardWidth)
            .wrapContentHeight()
            .onFocusChanged { isFocused = it.isFocused }
            .let { if (isFocused) it.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else it }
            .graphicsLayer { clip = true; shape = RoundedCornerShape(8.dp) }
            .background(Color.Transparent)
            .clickable(onClickLabel = "Open details", role = Role.Button, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer { clip = true; shape = RoundedCornerShape(8.dp) }
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val innerImageScale by animateFloatAsState(
                targetValue = if (isFocused && device.isTv) 1.08f else 1.0f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                label = "ImageZoom"
            )
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(media.coverImage.best)
                    .crossfade(false)
                    .allowHardware(true)
                    .size(225, 337)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = innerImageScale
                    scaleY = innerImageScale
                },
                contentScale = ContentScale.Crop,
            )
            media.averageScore?.let { score ->
                RatingBadge(score, Modifier.align(Alignment.TopStart).padding(5.dp))
            }
            if (media.isAdult) {
                AdultBadge(Modifier.align(Alignment.TopEnd).padding(5.dp))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(top = 6.dp, start = 4.dp, end = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = media.title.preferred,
                style = titleStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                modifier = Modifier
                    .then(if (device.isTv) Modifier.heightIn(min = tvTitleMinHeight) else Modifier),
            )
            Text(
                text = listOfNotNull(
                    media.format?.replace('_', ' '),
                    media.seasonYear?.toString(),
                    media.episodes?.let { "$it EP" },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun AdultBadge(modifier: Modifier = Modifier) {
    Text(
        "18+",
        modifier = modifier
            .semantics { contentDescription = "Adult content" }
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onError,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun RatingBadge(score: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .semantics { contentDescription = "Rated $score percent" }
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = .78f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp).padding(end = 2.dp),
        )
        Text(
            "$score%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

val GridContentPadding = PaddingValues(16.dp)
