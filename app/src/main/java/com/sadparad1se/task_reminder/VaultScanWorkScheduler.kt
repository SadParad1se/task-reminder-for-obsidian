package com.sadparad1se.task_reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules and cancels the persistent WorkManager job that scans selected vaults. */
class VaultScanWorkScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** Schedules periodic scans from current settings or cancels work when there are no vaults. */
    fun schedule(settings: AppSettings) {
        if (settings.vaultUris.isEmpty()) {
            cancel()
            return
        }

        val request = PeriodicWorkRequestBuilder<VaultScanWorker>(
            settings.scanFrequency.repeatIntervalMinutes,
            TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            UniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Cancels periodic vault scanning. */
    fun cancel() {
        workManager.cancelUniqueWork(UniqueWorkName)
    }

    private companion object {
        const val UniqueWorkName = "vault_periodic_scan"
    }
}
