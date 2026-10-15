package com.sadparad1se.task_reminder

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

/** Restarts periodic timing only after a complete, successful manual scan. */
internal class ManualVaultRescanner(
    private val settingsRepository: SettingsRepository,
    private val scanVaults: suspend (List<String>) -> Boolean,
    private val restartSchedule: suspend (AppSettings, Long) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun rescan(): Boolean {
        val vaults = settingsRepository.settingsFlow.first().vaultUris
        if (vaults.isEmpty()) return false
        if (!scanVaults(vaults)) return false
        val completedAt = now()
        TaskActivityMonitor.mutex.withLock {
            val latest = settingsRepository.settingsFlow.first()
            // A newly selected vault must be scanned before postponing its background scan.
            if (vaults.containsAll(latest.vaultUris)) restartSchedule(latest, completedAt)
        }
        return true
    }
}
