package com.sadparad1se.task_reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import java.util.concurrent.TimeUnit

/** Schedules and cancels the persistent WorkManager job that scans selected vaults. */
class VaultScanWorkScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** Schedules periodic scans from current settings or cancels work when there are no vaults. */
    fun schedule(settings: AppSettings, clearNextScanOverride: Boolean = false): Operation {
        if (settings.vaultUris.isEmpty()) {
            return cancel()
        }

        val request = PeriodicWorkRequestBuilder<VaultScanWorker>(
            settings.scanFrequency.repeatIntervalMinutes,
            TimeUnit.MINUTES
        ).apply {
            if (clearNextScanOverride) clearNextScheduleTimeOverride()
        }.build()

        return workManager.enqueueUniquePeriodicWork(
            UniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Delays the next background scan by a full interval without interrupting running work. */
    suspend fun restartAfterManualScan(settings: AppSettings, completedAt: Long) {
        if (settings.vaultUris.isEmpty()) {
            cancel().await()
            return
        }
        val request = PeriodicWorkRequestBuilder<VaultScanWorker>(
            settings.scanFrequency.repeatIntervalMinutes,
            TimeUnit.MINUTES
        ).setNextScheduleTimeOverride(
            completedAt + TimeUnit.MINUTES.toMillis(settings.scanFrequency.repeatIntervalMinutes)
        ).build()
        workManager.enqueueUniquePeriodicWork(
            UniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        ).await()
    }

    /** Cancels periodic vault scanning. */
    fun cancel(): Operation {
        return workManager.cancelUniqueWork(UniqueWorkName)
    }

    internal companion object {
        const val UniqueWorkName = "vault_periodic_scan"
    }
}
