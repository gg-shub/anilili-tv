package com.shubh.anililitv.ui.watch

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shubh.anililitv.ui.adaptive.focusHighlight

@Composable
fun SourceFilterRow(
    audio: AudioFilter,
    onAudioChange: (AudioFilter) -> Unit,
    languages: List<String>,
    language: String?,
    onLanguageChange: (String?) -> Unit,
    stillChecking: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AudioFilter.entries.forEach { option ->
                FilterChip(
                    selected = audio == option,
                    onClick = { onAudioChange(option) },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.focusHighlight(RoundedCornerShape(20.dp)),
                )
            }
        }

        if (languages.isNotEmpty() || stillChecking) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (stillChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    if (stillChecking) "Checking servers for languages…" else "Language",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = language == null,
                    onClick = { onLanguageChange(null) },
                    label = { Text("Any") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.focusHighlight(RoundedCornerShape(20.dp)),
                )
                languages.forEach { option ->
                    FilterChip(
                        selected = language == option,
                        onClick = { onLanguageChange(if (language == option) null else option) },
                        label = { Text(option) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier
                            .padding(end = 0.dp)
                            .focusHighlight(RoundedCornerShape(20.dp)),
                    )
                }
            }
        }
    }
}
