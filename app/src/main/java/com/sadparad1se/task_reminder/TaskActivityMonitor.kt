package com.sadparad1se.task_reminder

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

/** Serializes scans and maintains one inactivity reminder cycle across selected vaults. */
internal class TaskActivityMonitor(
    private val settingsRepository: SettingsRepository,
    private val notifier: StaleTaskUpdateNotifier,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun scanVaults(
        vaultUris: List<String>,
        scanVault: suspend (String) -> VaultScanResult,
        rescheduleNotifications: suspend () -> Unit
    ): Boolean = mutex.withLock {
        val selected = settingsRepository.settingsFlow.first().vaultUris.toSet()
        val successful = mutableSetOf<String>()
        var allSucceeded = true
        vaultUris.distinct().filter { it in selected }.forEach { vaultUri ->
            val result = scanVault(vaultUri)
            if (result.succeeded) {
                successful.add(vaultUri)
                if (settingsRepository.recordTaskActivityScan(result.scanStartedAt, result.taskActivityFound)) {
                    notifier.cancel()
                }
            } else {
                allSucceeded = false
            }
        }
        rescheduleNotifications()

        val settings = settingsRepository.settingsFlow.first()
        if (settings.vaultUris.isNotEmpty() && successful.containsAll(settings.vaultUris)) {
            val timestamp = now()
            if (isStaleTaskReminderDue(settings, timestamp) && notifier.notifyNoRecentTaskUpdates()) {
                settingsRepository.updateLastStaleTaskUpdateNotificationAt(timestamp)
            }
        }
        allSucceeded
    }

    companion object {
        // Shared by all repository instances and settings changes affecting the reminder cycle.
        val mutex = Mutex()
    }
}

/** Reminders are anchored to activity, rather than drifting with delayed background scans. */
internal fun isStaleTaskReminderDue(settings: AppSettings, now: Long): Boolean {
    val baseline = settings.lastTaskActivityScanAt
    if (!settings.staleTaskUpdateNotificationsEnabled || baseline <= 0L || now < baseline) return false
    val interval = Duration.ofHours(24).toMillis()
    val currentWindow = (now - baseline) / interval
    val deliveredWindow = if (settings.lastStaleTaskUpdateNotificationAt >= baseline) {
        (settings.lastStaleTaskUpdateNotificationAt - baseline) / interval
    } else {
        0L
    }
    return currentWindow > deliveredWindow
}
