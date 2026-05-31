package com.sadparad1se.task_reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Displays the task list and top-level navigation actions. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    tasks: List<TaskListItem>,
    priorities: List<String>,
    priorityColors: Map<String, String?>,
    excludedPriorities: Set<String>,
    includedTimeBuckets: Set<TaskTimeBucket>,
    onPriorityFilterToggle: (String) -> Unit,
    onTimeBucketToggle: (TaskTimeBucket) -> Unit,
    onTaskClick: (TaskListItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Reminder") },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text("Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            if (showScrollToTop) {
                FloatingActionButton(
                    onClick = {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                ) {
                    Text("↑")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                TaskFilterRows(
                    priorities = priorities,
                    priorityColors = priorityColors,
                    excludedPriorities = excludedPriorities,
                    includedTimeBuckets = includedTimeBuckets,
                    onPriorityFilterToggle = onPriorityFilterToggle,
                    onTimeBucketToggle = onTimeBucketToggle
                )
            }

            if (tasks.isEmpty()) {
                item {
                    Text(
                        text = "No tasks found.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            } else {
                items(tasks) { task ->
                    TaskRow(
                        task = task,
                        onClick = { onTaskClick(task) }
                    )
                }
            }
        }
    }
}

/** Displays priority and time bucket toggles above the task cards. */
@Composable
private fun TaskFilterRows(
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

/** Displays a clickable filter button that uses colored text for included values. */
@Composable
private fun FilterButton(
    label: String,
    active: Boolean,
    activeColor: Color?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val enabledColor = activeColor ?: MaterialTheme.colorScheme.onSurface
    val colors = if (active) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = enabledColor
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            contentColor = enabledColor.copy(alpha = 0.38f)
        )
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** Returns the fixed filter color for a time bucket button. */
private fun TaskTimeBucket.filterColor(): Color {
    return when (this) {
        TaskTimeBucket.UNDATED -> Color(0xFF607D8B)
        TaskTimeBucket.UPCOMING -> Color(0xFFFFC107)
        TaskTimeBucket.CURRENT -> Color(0xFF2E7D32)
        TaskTimeBucket.OVERDUE -> Color(0xFFC62828)
    }
}

/** Displays one tappable task summary card. */
@Composable
private fun TaskRow(
    task: TaskListItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall
            )
            task.scheduled?.let { scheduled ->
                Text(
                    text = "Scheduled: $scheduled",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            task.due?.let { due ->
                Text(
                    text = "Due: $due",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (task.status != null || task.priority != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.status?.let { status ->
                        TaskMetadataChip(
                            label = "Status: $status",
                            hexColor = task.statusColor
                        )
                    }
                    task.priority?.let { priority ->
                        TaskMetadataChip(
                            label = "Priority: $priority",
                            hexColor = task.priorityColor
                        )
                    }
                }
            }
            if (task.tags.isNotEmpty()) {
                Text(
                    text = "Tags: ${task.tags.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = task.excerpt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Displays one status or priority label using its TaskNotes color when available. */
@Composable
private fun TaskMetadataChip(
    label: String,
    hexColor: String?
) {
    val backgroundColor = parseTaskNotesColor(hexColor) ?: MaterialTheme.colorScheme.surfaceVariant
    Surface(
        color = backgroundColor,
        contentColor = readableContentColor(backgroundColor),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** Parses a TaskNotes hex color string into a Compose color. */
private fun parseTaskNotesColor(hexColor: String?): Color? {
    val color = hexColor
        ?.trim()
        ?.removePrefix("#")
        ?.takeIf { it.length == 6 || it.length == 8 }
        ?.takeIf { value -> value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } }
        ?: return null
    val argb = if (color.length == 6) "FF$color" else color
    return Color(argb.toULong(16).toLong().toInt())
}

/** Chooses black or white text based on the chip background brightness. */
private fun readableContentColor(backgroundColor: Color): Color {
    return if (backgroundColor.luminance() > 0.5f) Color.Black else Color.White
}
