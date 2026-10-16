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

/** Converts the persistence model into the display model used by Compose screens. */
internal fun StoredTask.toTaskListItem(): TaskListItem {
    return TaskListItem(
        title = title,
        due = due,
        scheduled = scheduled,
        excerpt = excerpt,
        vaultUri = vaultUri,
        fileUri = fileUri,
        filePath = filePath,
        fileName = fileName,
        status = status,
        statusColor = statusColor,
        priority = priority,
        priorityColor = priorityColor,
        priorityWeight = priorityWeight,
        dateCreated = dateCreated,
        dateModified = dateModified,
        tags = tags.splitStoredTags(),
        lastModified = lastModified
    )
}

/** Splits the newline-delimited tag format stored in Room into display tags. */
private fun String.splitStoredTags(): List<String> {
    return lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
}
