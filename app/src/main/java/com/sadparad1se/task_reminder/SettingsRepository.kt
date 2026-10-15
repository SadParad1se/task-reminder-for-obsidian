package com.sadparad1se.task_reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Reads and writes persistent app settings from DataStore. */
class SettingsRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = context.settingsDataStore
) {

    private object Keys {
        val VAULT_URIS = stringSetPreferencesKey("vault_uris")
        val SCAN_FREQUENCY = stringPreferencesKey("scan_frequency")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SCHEDULED_DATETIME_NOTIFICATIONS_ENABLED =
            booleanPreferencesKey("scheduled_datetime_notifications_enabled")
        val SCHEDULED_DATE_DEFAULT_NOTIFICATION_TIME =
            stringPreferencesKey("scheduled_date_default_notification_time")
        val DUE_DATE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("due_date_notifications_enabled")
        val DUE_DATE_DEFAULT_NOTIFICATION_TIME = stringPreferencesKey("due_date_default_notification_time")
        val STALE_TASK_UPDATE_NOTIFICATIONS_ENABLED =
            booleanPreferencesKey("stale_task_update_notifications_enabled")
        val LAST_STALE_TASK_UPDATE_NOTIFICATION_AT =
            longPreferencesKey("last_stale_task_update_notification_at")
        val LAST_TASK_ACTIVITY_SCAN_AT = longPreferencesKey("last_task_activity_scan_at")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val vaultUris = preferences[Keys.VAULT_URIS]
                ?.toList()
                ?.sorted()
                ?: emptyList()
            val scanFrequency = ScanFrequency.fromStorageValue(preferences[Keys.SCAN_FREQUENCY])

            AppSettings(
                vaultUris = vaultUris,
                scanFrequency = scanFrequency,
                onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED] ?: false,
                scheduledDateTimeNotificationsEnabled =
                    preferences[Keys.SCHEDULED_DATETIME_NOTIFICATIONS_ENABLED] ?: true,
                scheduledDateDefaultNotificationTime =
                    preferences[Keys.SCHEDULED_DATE_DEFAULT_NOTIFICATION_TIME] ?: "07:00",
                dueDateNotificationsEnabled = preferences[Keys.DUE_DATE_NOTIFICATIONS_ENABLED] ?: true,
                dueDateDefaultNotificationTime = preferences[Keys.DUE_DATE_DEFAULT_NOTIFICATION_TIME] ?: "07:00",
                staleTaskUpdateNotificationsEnabled =
                    preferences[Keys.STALE_TASK_UPDATE_NOTIFICATIONS_ENABLED] ?: true,
                lastStaleTaskUpdateNotificationAt =
                    preferences[Keys.LAST_STALE_TASK_UPDATE_NOTIFICATION_AT] ?: 0L,
                lastTaskActivityScanAt = preferences[Keys.LAST_TASK_ACTIVITY_SCAN_AT] ?: 0L
            )
        }

    /** Adds a vault URI to the selected vault set. */
    suspend fun addVaultUri(vaultUri: String) {
        TaskActivityMonitor.mutex.withLock {
            if (vaultUri !in settingsFlow.first().vaultUris) {
                // Re-adding a vault establishes a new initial scan, even if old scan state remains.
                TaskDatabase.getInstance(context).vaultScanStateDao().deleteByVaultUri(vaultUri)
            }
            dataStore.edit { preferences ->
                preferences[Keys.VAULT_URIS] = preferences[Keys.VAULT_URIS].orEmpty() + vaultUri
            }
        }
        scheduleVaultScanWork()
    }

    /** Removes a vault URI from the selected vault set. */
    suspend fun removeVaultUri(vaultUri: String) {
        TaskActivityMonitor.mutex.withLock {
            dataStore.edit { preferences ->
                val remaining = preferences[Keys.VAULT_URIS].orEmpty() - vaultUri
                preferences[Keys.VAULT_URIS] = remaining
                if (remaining.isEmpty()) {
                    preferences.remove(Keys.LAST_TASK_ACTIVITY_SCAN_AT)
                    preferences.remove(Keys.LAST_STALE_TASK_UPDATE_NOTIFICATION_AT)
                }
            }
            if (settingsFlow.first().vaultUris.isEmpty()) StaleTaskUpdateNotifier(context).cancel()
        }
        scheduleVaultScanWork()
    }

    /** Persists the user's chosen vault scan frequency. */
    suspend fun updateScanFrequency(scanFrequency: ScanFrequency) {
        dataStore.edit { preferences ->
            preferences[Keys.SCAN_FREQUENCY] = scanFrequency.storageValue
        }
        scheduleVaultScanWork()
    }

    /** Keeps the persistent background scan job aligned with the saved vault settings. */
    private suspend fun scheduleVaultScanWork() {
        VaultScanWorkScheduler(context).schedule(settingsFlow.first())
    }

    /** Persists whether exact notifications for scheduled date-time values are enabled. */
    suspend fun updateScheduledDateTimeNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SCHEDULED_DATETIME_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    /** Persists the default notification time for scheduled date-only values. */
    suspend fun updateScheduledDateDefaultNotificationTime(time: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SCHEDULED_DATE_DEFAULT_NOTIFICATION_TIME] = time
        }
    }

    /** Persists whether due-date notifications are enabled. */
    suspend fun updateDueDateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.DUE_DATE_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    /** Persists the default notification time for due date reminders. */
    suspend fun updateDueDateDefaultNotificationTime(time: String) {
        dataStore.edit { preferences ->
            preferences[Keys.DUE_DATE_DEFAULT_NOTIFICATION_TIME] = time
        }
    }

    /** Persists whether stale task update notifications are enabled. */
    suspend fun updateStaleTaskUpdateNotificationsEnabled(enabled: Boolean) {
        TaskActivityMonitor.mutex.withLock {
            dataStore.edit { preferences ->
                preferences[Keys.STALE_TASK_UPDATE_NOTIFICATIONS_ENABLED] = enabled
            }
            if (!enabled) StaleTaskUpdateNotifier(context).cancel()
        }
    }

    /** Initializes tracking or records a newer scan that found activity, even while reminders are off. */
    internal suspend fun recordTaskActivityScan(scanStartedAt: Long, taskActivityFound: Boolean): Boolean {
        var changed = false
        dataStore.edit { preferences ->
            val previous = preferences[Keys.LAST_TASK_ACTIVITY_SCAN_AT] ?: 0L
            if (scanStartedAt > previous && (previous == 0L || taskActivityFound)) {
                preferences[Keys.LAST_TASK_ACTIVITY_SCAN_AT] = scanStartedAt
                preferences.remove(Keys.LAST_STALE_TASK_UPDATE_NOTIFICATION_AT)
                changed = true
            }
        }
        return changed
    }

    /** Records when the stale task update notification was last shown. */
    suspend fun updateLastStaleTaskUpdateNotificationAt(timestampMillis: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.LAST_STALE_TASK_UPDATE_NOTIFICATION_AT] = timestampMillis
        }
    }

    /** Marks onboarding as completed so the home screen is shown next launch. */
    suspend fun completeOnboarding() {
        dataStore.edit { preferences ->
            preferences[Keys.ONBOARDING_COMPLETED] = true
        }
    }
}
