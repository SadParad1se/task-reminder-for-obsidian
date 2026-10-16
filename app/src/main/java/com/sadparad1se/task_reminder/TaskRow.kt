package com.sadparad1se.task_reminder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Displays one tappable task summary card. */
@Composable
internal fun TaskRow(
    task: TaskListItem,
    onClick: () -> Unit,
    onNotifyClick: () -> Unit,
    notificationSuccessCount: Long
) {
    val accentColor = parseTaskNotesColor(task.priorityColor)
        ?: parseTaskNotesColor(task.statusColor)
        ?: MaterialTheme.colorScheme.primary
    val priorityBorderColor = parseTaskNotesColor(task.priorityColor) ?: accentColor
    val timeBucketBorderColor = task.timeBucket().filterColor()
    val cardShape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        priorityBorderColor.copy(alpha = 0.72f),
                        timeBucketBorderColor.copy(alpha = 0.72f)
                    )
                ),
                shape = cardShape
            )
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = borderOnlyContainerColor(),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                task.scheduled?.let { scheduled ->
                    Text(
                        text = "📅 $scheduled",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                task.due?.let { due ->
                    Text(
                        text = "🏁 $due",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        task.status?.let { status ->
                            TaskMetadataChip(
                                label = status,
                                hexColor = task.statusColor
                            )
                        }
                        if (task.tags.isNotEmpty()) {
                            TaskMetadataChip(
                                label = task.tags.joinToString(", "),
                                hexColor = null
                            )
                        }
                    }
                    TaskNotificationBell(
                        fileUri = task.fileUri,
                        taskTitle = task.title,
                        successCount = notificationSuccessCount,
                        onClick = onNotifyClick,
                    )
                }
            }
        }
    }
}

/** Returns a neutral card/button fill that stays readable in light and dark themes. */
@Composable
private fun borderOnlyContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
}

/** Displays one status or priority label using its TaskNotes color when available. */
@Composable
private fun TaskMetadataChip(
    label: String,
    hexColor: String?
) {
    val backgroundColor = parseTaskNotesColor(hexColor) ?: MaterialTheme.colorScheme.primary
    Surface(
        color = backgroundColor.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, backgroundColor.copy(alpha = 0.72f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
