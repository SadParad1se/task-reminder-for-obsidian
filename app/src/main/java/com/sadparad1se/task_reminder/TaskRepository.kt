package com.sadparad1se.task_reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Coordinates task persistence, vault scanning, and notification scheduling. */
class TaskRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = TaskDatabase.getInstance(appContext)
    private val settingsRepository = SettingsRepository(appContext)
    private val taskActivityMonitor = TaskActivityMonitor(settingsRepository, StaleTaskUpdateNotifier(appContext))
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
        return scanVaults(listOf(vaultUri))
    }

    /** Scans each selected vault sequentially. */
    suspend fun scanVaults(vaultUris: List<String>): Boolean {
        return taskActivityMonitor.scanVaults(vaultUris, { vaultUri ->
            val configurationFolder = settingsRepository.settingsFlow.first()
                .vaultConfigurationFolders.getValue(vaultUri)
            scanner.scanVault(vaultUri, configurationFolder)
        }, notificationScheduler::rescheduleAll)
    }

    /** Manually scans all selected vaults and restarts background timing after success. */
    suspend fun rescanVaults(): Boolean = ManualVaultRescanner(
        settingsRepository,
        this::scanVaults,
        VaultScanWorkScheduler(appContext)::restartAfterManualScan
    ).rescan()

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

    /** Posts the current stored version of a task without rescheduling any alarms. */
    internal suspend fun postTaskNotification(fileUri: String): TaskNotificationResult {
        val task = database.taskDao().getByFileUri(fileUri) ?: return TaskNotificationResult.TASK_NOT_FOUND
        return TaskNotifier(appContext).postManual(task)
    }
}
