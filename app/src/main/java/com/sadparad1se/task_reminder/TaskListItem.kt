package com.sadparad1se.task_reminder

/**
 * UI-facing task summary shown in the home task list.
 *
 * Unlike [StoredTask], this model is not a Room entity and keeps tags as a parsed list for display.
 */
data class TaskListItem(
    val title: String,
    val scheduled: String?,
    val due: String?,
    val excerpt: String,
    val vaultUri: String = "",
    val fileUri: String = "",
    val filePath: String = "",
    val fileName: String = "",
    val status: String? = null,
    val statusColor: String? = null,
    val priority: String? = null,
    val priorityColor: String? = null,
    val priorityWeight: Int? = null,
    val dateCreated: String? = null,
    val dateModified: String? = null,
    val tags: List<String> = emptyList(),
    val lastModified: Long? = null
)
