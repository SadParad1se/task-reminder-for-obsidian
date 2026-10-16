package com.sadparad1se.task_reminder

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Returns a future scheduled reminder, respecting the separate date-time setting. */
internal fun scheduledReminderTimeMillis(
    scheduled: String?,
    settings: AppSettings,
    now: Long,
    zone: ZoneId
): Long? {
    if (scheduled.isNullOrBlank()) return null
    val triggerAt = scheduled.toTaskNotesDateTimeEpochMillisOrNull(zone)
        ?.takeIf { settings.scheduledDateTimeNotificationsEnabled }
        ?: dateReminderTimeMillis(scheduled, settings.scheduledDateDefaultNotificationTime, zone)
    return triggerAt?.takeIf { it > now }
}

/** Returns a future due reminder when due notifications are enabled. */
internal fun dueReminderTimeMillis(
    due: String?,
    settings: AppSettings,
    now: Long,
    zone: ZoneId
): Long? {
    if (!settings.dueDateNotificationsEnabled || due.isNullOrBlank()) return null
    val triggerAt = due.toTaskNotesDateTimeEpochMillisOrNull(zone)
        ?: dateReminderTimeMillis(due, settings.dueDateDefaultNotificationTime, zone)
    return triggerAt?.takeIf { it > now }
}

private fun dateReminderTimeMillis(date: String, time: String, zone: ZoneId): Long? = runCatching {
    LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        .atTime(parseNotificationTime(time))
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}.getOrNull()
