package com.shubh.anililitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubh.anililitv.data.settings.CaptionBackgroundColor
import com.shubh.anililitv.data.settings.CaptionEdgeStyle
import com.shubh.anililitv.data.settings.CaptionStyle
import com.shubh.anililitv.data.settings.CaptionTextColor
import com.shubh.anililitv.data.settings.SettingsStore
import com.shubh.anililitv.ui.adaptive.focusHighlight

private const val PREVIEW_SAMPLE = "The quick brown fox jumps"

private const val PREVIEW_BASE_SP = 15f

@Composable
fun CaptionAppearanceEditor(
    modifier: Modifier = Modifier,
    footnote: String? = null,
) {
    val style by SettingsStore.captionStyle.collectAsState()
    Column(modifier.fillMaxWidth()) {
        CaptionPreview(style)
        CaptionChoiceRow(
            label = "Background opacity",
            options = CaptionStyle.BACKGROUND_OPACITY_STEPS,
            selected = style.backgroundOpacityPercent,
            labelOf = { if (it == 0) "Off" else "$it%" },
            onSelect = SettingsStore::setCaptionBackgroundOpacity,
        )
        CaptionChoiceRow(
            label = "Background color",
            options = CaptionBackgroundColor.entries,
            selected = style.backgroundColor,
            labelOf = CaptionBackgroundColor::label,
            onSelect = SettingsStore::setCaptionBackgroundColor,
        )
        CaptionChoiceRow(
            label = "Text size",
            options = CaptionStyle.TEXT_SCALE_STEPS,
            selected = style.textScalePercent,
            labelOf = { "$it%" },
            onSelect = SettingsStore::setCaptionTextScale,
        )
        CaptionChoiceRow(
            label = "Text weight",
            options = listOf(false, true),
            selected = style.boldText,
            labelOf = { if (it) "Bold" else "Regular" },
            onSelect = SettingsStore::setCaptionBold,
        )
        CaptionChoiceRow(
            label = "Bottom margin",
            options = CaptionStyle.BOTTOM_MARGIN_STEPS,
            selected = style.bottomMarginPercent,
            labelOf = { "$it%" },
            onSelect = SettingsStore::setCaptionBottomMargin,
        )
        CaptionChoiceRow(
            label = "Text color",
            options = CaptionTextColor.entries,
            selected = style.textColor,
            labelOf = CaptionTextColor::label,
            onSelect = SettingsStore::setCaptionTextColor,
        )
        CaptionChoiceRow(
            label = "Edge style",
            options = CaptionEdgeStyle.entries,
            selected = style.edgeStyle,
            labelOf = CaptionEdgeStyle::label,
            onSelect = SettingsStore::setCaptionEdgeStyle,
        )
        if (footnote != null) {
            Text(
                footnote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
fun CaptionAppearanceDialog(
    onDismiss: () -> Unit,
    footnote: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Caption appearance") },
        containerColor = Color(0xFF141414),
        shape = RoundedCornerShape(8.dp),
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                CaptionAppearanceEditor(footnote = footnote)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = SettingsStore::resetCaptionStyle, shape = RoundedCornerShape(8.dp)) { Text("Reset") }
        },
    )
}

@Composable
private fun CaptionPreview(style: CaptionStyle, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A4E6B), Color(0xFF0E1116))))
            .heightIn(min = 120.dp)
            .padding(
                start = 12.dp,
                end = 12.dp,
                bottom = (style.bottomMarginPercent * 0.6f).dp,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .background(Color(style.backgroundArgb), RoundedCornerShape(2.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            val fontSize = (PREVIEW_BASE_SP * style.textScalePercent / 100f).sp
            if (style.edgeStyle == CaptionEdgeStyle.OUTLINE) {
                Text(
                    PREVIEW_SAMPLE,
                    color = Color.Black,
                    fontSize = fontSize,
                    fontWeight = if (style.boldText) FontWeight.Bold else FontWeight.Normal,
                    style = TextStyle(drawStyle = Stroke(width = 5f)),
                )
            }
            Text(
                PREVIEW_SAMPLE,
                color = Color(style.textArgb),
                fontSize = fontSize,
                fontWeight = if (style.boldText) FontWeight.Bold else FontWeight.Normal,
                style = if (style.edgeStyle == CaptionEdgeStyle.DROP_SHADOW) {
                    TextStyle(shadow = Shadow(Color.Black, Offset(2f, 2f), blurRadius = 4f))
                } else {
                    TextStyle.Default
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> CaptionChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            options.forEach { option ->
                com.shubh.anililitv.ui.components.CustomFilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = labelOf(option),
                )
            }
        }
    }
}
