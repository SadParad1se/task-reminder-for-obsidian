package com.sadparad1se.task_reminder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Displays priority and time bucket toggles above the task cards. */
@Composable
internal fun TaskFilterRows(
    priorities: List<String>,
    priorityColors: Map<String, String?>,
    excludedPriorities: Set<String>,
    includedTimeBuckets: Set<TaskTimeBucket>,
    onPriorityFilterToggle: (String) -> Unit,
    onTimeBucketToggle: (TaskTimeBucket) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (priorities.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                priorities.forEach { priority ->
                    FilterButton(
                        label = priority,
                        active = priority !in excludedPriorities,
                        activeColor = parseTaskNotesColor(priorityColors[priority]),
                        modifier = Modifier.weight(1f),
                        onClick = { onPriorityFilterToggle(priority) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TaskTimeBucket.entries.forEach { bucket ->
                FilterButton(
                    label = bucket.label,
                    active = bucket in includedTimeBuckets,
                    activeColor = bucket.filterColor(),
                    modifier = Modifier.weight(1f),
                    onClick = { onTimeBucketToggle(bucket) }
                )
            }
        }
    }
}

/** Displays a clickable filter button that uses color only on its border. */
@Composable
private fun FilterButton(
    label: String,
    active: Boolean,
    activeColor: Color?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val enabledColor = activeColor ?: MaterialTheme.colorScheme.primary
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = filterButtonContainerColor(enabledColor, active),
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(
            width = 3.dp,
            color = enabledColor.copy(alpha = if (active) 0.76f else 0.28f)
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** Returns a muted color fill for filters while preserving contrast in both themes. */
@Composable
private fun filterButtonContainerColor(
    color: Color,
    active: Boolean
): Color {
    val alpha = when {
        active && isSystemInDarkTheme() -> 0.24f
        active -> 0.14f
        isSystemInDarkTheme() -> 0.10f
        else -> 0.06f
    }
    return color.copy(alpha = alpha)
}
