package com.sadparad1se.task_reminder

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a TaskNotes task persisted from a Markdown file.
 *
 * This database model stores tags as a newline-delimited string to keep Room persistence simple.
 */
@Entity(
    tableName = "tasks",
    indices = [Index(value = ["fileUri"], unique = true)]
)
data class StoredTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vaultUri: String,
    val fileUri: String,
    val filePath: String,
    val fileName: String,
    val title: String,
    val excerpt: String,
    val status: String?,
    val statusColor: String?,
    val priority: String?,
    val priorityColor: String?,
    val priorityWeight: Int?,
    val scheduled: String?,
    val due: String?,
    val dateCreated: String?,
    val dateModified: String?,
    val tags: String,
    val lastModified: Long?
)
