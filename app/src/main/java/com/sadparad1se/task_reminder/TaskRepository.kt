package com.sadparad1se.task_reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Coordinates task persistence, vault scanning, and notification scheduling. */
class TaskRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = TaskDatabase.getInstance(appContext)
    private val settingsRepository = SettingsRepository(appContext)
    private val staleTaskUpdateNotifier = StaleTaskUpdateNotifier(appContext)
    private val notificationScheduler = TaskNotificationScheduler(
        context = appContext,
        taskDao = database.taskDao()
    )
    private val scanner = VaultScanner(
        context = appContext,
        taskDao = database.taskDao(),
        vaultScanStateDao = database.vaultScanStateDao(),
        notificationScheduler = notificationScheduler
    )

    val taskPrioritiesFlow: Flow<List<String>> = database.taskDao()
        .observeTaskPriorities()

    /** Observes stored tasks after applying the selected exclusion filters. */
    fun tasksFlow(
        excludedPriorities: Set<String>,
        includedTimeBuckets: Set<TaskTimeBucket>
    ): Flow<List<TaskListItem>> {
        val normalizedExcludedPriorities = excludedPriorities.map { it.trim().lowercase() }
        return database.taskDao()
            .observeTasks(
                excludedPriorities = normalizedExcludedPriorities,
                excludedPrioritiesEmpty = normalizedExcludedPriorities.isEmpty(),
                includeUndated = TaskTimeBucket.UNDATED in includedTimeBuckets,
                includeUpcoming = TaskTimeBucket.UPCOMING in includedTimeBuckets,
                includeCurrent = TaskTimeBucket.CURRENT in includedTimeBuckets,
                includeOverdue = TaskTimeBucket.OVERDUE in includedTimeBuckets,
                now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
            .map { tasks -> tasks.map { it.toTaskListItem() } }
    }
    val vaultScanStatesFlow: Flow<List<VaultScanState>> = database.vaultScanStateDao()
        .observeAll()

    /** Scans one vault and updates stored tasks from TaskNotes Markdown files. */
    suspend fun scanVault(vaultUri: String): Boolean {
        val scanSucceeded = scanner.scanVault(vaultUri)
        notificationScheduler.rescheduleAll()
        if (scanSucceeded) {
            notifyIfTasksHaveNotChangedRecently(database.taskDao().getTasksByVaultUri(vaultUri))
        }
        return scanSucceeded
    }

    /** Scans each selected vault sequentially. */
    suspend fun scanVaults(vaultUris: List<String>): Boolean {
        var allScansSucceeded = true
        vaultUris.forEach { vaultUri ->
            if (!scanner.scanVault(vaultUri)) {
                allScansSucceeded = false
            }
        }
        notificationScheduler.rescheduleAll()
        if (allScansSucceeded) {
            notifyIfTasksHaveNotChangedRecently(database.taskDao().getAllTasks())
        }
        return allScansSucceeded
    }

    /** Sends a reminder when all scanned tasks are older than the stale update threshold. */
    private suspend fun notifyIfTasksHaveNotChangedRecently(tasks: List<StoredTask>) {
        if (tasks.isEmpty()) return

        val settings = settingsRepository.settingsFlow.first()
        if (!settings.staleTaskUpdateNotificationsEnabled) return

        val now = System.currentTimeMillis()
        if (now - settings.lastStaleTaskUpdateNotificationAt < StaleTaskNotificationCooldown.toMillis()) return

        val newestTaskUpdateAt = tasks.maxOfOrNull { task ->
            task.dateModified?.toTaskNotesEpochMillisOrNull() ?: task.lastModified ?: 0L
        } ?: return
        if (newestTaskUpdateAt <= 0L) return
        if (now - newestTaskUpdateAt < StaleTaskUpdateThreshold.toMillis()) return

        staleTaskUpdateNotifier.notifyNoRecentTaskUpdates()
        settingsRepository.updateLastStaleTaskUpdateNotificationAt(now)
    }

    /** Deletes all app-owned data and permissions linked to one removed vault. */
    suspend fun deleteVaultAppData(vaultUri: String) {
        database.taskDao().getTasksByVaultUri(vaultUri)
            .forEach { task -> notificationScheduler.cancelTaskNotifications(task.id) }
        database.taskDao().deleteByVaultUri(vaultUri)
        database.vaultScanStateDao().deleteByVaultUri(vaultUri)
        scanner.clearCachedTaskNotesSettings(vaultUri)
        releaseVaultReadPermission(vaultUri)
    }

    /** Releases the persisted Storage Access Framework read permission for a removed vault. */
    private fun releaseVaultReadPermission(vaultUri: String) {
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                Uri.parse(vaultUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    /** Rebuilds scheduled alarms for all stored tasks. */
    suspend fun rescheduleAllNotifications() {
        notificationScheduler.rescheduleAll()
    }

    /** Returns whether Android currently allows this app to schedule exact alarms. */
    fun canScheduleExactAlarms(): Boolean = notificationScheduler.canScheduleExactAlarms()

    /** Returns whether Android currently allows this app to post notifications. */
    fun hasNotificationPermission(): Boolean = notificationScheduler.hasNotificationPermission()

    /** Converts the persistence model into the display model used by Compose screens. */
    private fun StoredTask.toTaskListItem(): TaskListItem {
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

    private companion object {
        val StaleTaskUpdateThreshold: Duration = Duration.ofHours(24)
        val StaleTaskNotificationCooldown: Duration = Duration.ofHours(24)
    }
}
