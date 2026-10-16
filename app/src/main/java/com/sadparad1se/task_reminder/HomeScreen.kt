package com.sadparad1se.task_reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    onNotifyTask: (TaskListItem) -> Unit,
    onRescan: () -> Unit,
    canRescan: Boolean,
    isScanning: Boolean,
    onOpenSettings: () -> Unit,
    notificationSuccessCounts: Map<String, Long> = emptyMap()
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    Scaffold(
        modifier = Modifier.background(appBackgroundBrush(0.86f)),
        containerColor = Color.Transparent,
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            TopAppBar(
                title = {
                    TextButton(
                        onClick = onRescan,
                        enabled = canRescan && !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.rescan),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.open_settings),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (showScrollToTop) {
                FloatingActionButton(
                    onClick = {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up),
                        contentDescription = stringResource(R.string.back_to_top),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
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
                items(tasks, key = { it.fileUri }) { task ->
                    TaskRow(
                        task = task,
                        onClick = { onTaskClick(task) },
                        onNotifyClick = { onNotifyTask(task) },
                        notificationSuccessCount = notificationSuccessCounts[task.fileUri] ?: 0L
                    )
                }
            }
        }
    }
}
