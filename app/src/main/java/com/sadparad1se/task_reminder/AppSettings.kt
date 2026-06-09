package com.sadparad1se.task_reminder

/** User-selectable cadence for periodic vault scans. */
enum class ScanFrequency(
    val storageValue: String,
    val label: String,
    val repeatIntervalMinutes: Long
) {
    EVERY_15_MINUTES("every_15_minutes", "Every 15 minutes", 15),
    EVERY_30_MINUTES("every_30_minutes", "Every 30 minutes", 30),
    EVERY_HOUR("every_hour", "Every hour", 60),
    EVERY_6_HOURS("every_6_hours", "Every 6 hours", 360),
    EVERY_12_HOURS("every_12_hours", "Every 12 hours", 720);

    companion object {
        /** Converts a persisted storage value into a scan frequency, defaulting to hourly. */
        fun fromStorageValue(value: String?): ScanFrequency {
            return entries.firstOrNull { it.storageValue == value } ?: EVERY_HOUR
        }
    }
}

/** Snapshot of user settings loaded from DataStore. */
data class AppSettings(
    val vaultUris: List<String> = emptyList(),
    val scanFrequency: ScanFrequency = ScanFrequency.EVERY_HOUR,
    val onboardingCompleted: Boolean = false,
    val scheduledDateTimeNotificationsEnabled: Boolean = true,
    val scheduledDateDefaultNotificationTime: String = "07:00",
    val dueDateNotificationsEnabled: Boolean = true,
    val dueDateDefaultNotificationTime: String = "07:00",
    val staleTaskUpdateNotificationsEnabled: Boolean = true,
    val lastStaleTaskUpdateNotificationAt: Long = 0L
)
