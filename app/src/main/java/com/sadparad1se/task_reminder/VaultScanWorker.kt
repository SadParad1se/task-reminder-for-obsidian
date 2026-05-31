package com.sadparad1se.task_reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

/** Background worker that scans all selected vaults using the latest saved settings. */
class VaultScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    /** Runs one periodic scan cycle for every configured vault. */
    override suspend fun doWork(): Result {
        return runCatching {
            val settings = SettingsRepository(applicationContext).settingsFlow.first()
            if (settings.vaultUris.isNotEmpty()) {
                if (!TaskRepository(applicationContext).scanVaults(settings.vaultUris)) {
                    error("One or more vault scans failed")
                }
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
