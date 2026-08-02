package com.shubh.anililitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.shubh.anililitv.ui.adaptive.focusHighlight

@Composable
fun AddToListDialog(
    currentStatus: String?,
    onStatusSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val statuses = listOf(
        "PLANNING" to "Plan to Watch",
        "CURRENT" to "Watching",
        "COMPLETED" to "Completed",
        "REPEATING" to "Rewatching",
        "PAUSED" to "Paused",
        "DROPPED" to "Dropped"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(300.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Add to List",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                statuses.forEach { (statusId, statusLabel) ->
                    val isSelected = currentStatus == statusId
                    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusHighlight(RoundedCornerShape(8.dp))
                            .background(bgColor, RoundedCornerShape(8.dp))
                            .clickable {
                                onStatusSelected(statusId)
                                onDismiss()
                            }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
