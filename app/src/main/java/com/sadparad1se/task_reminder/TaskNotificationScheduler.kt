package com.sadparad1se.task_reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Schedules and cancels exact Android alarms for task reminders. */
class TaskNotificationScheduler(
    private val context: Context,
    private val taskDao: TaskDao,
    private val settingsRepository: SettingsRepository = SettingsRepository(context)
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Returns whether exact alarms are currently allowed for this app. */
    fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    /** Returns whether task notifications can currently be posted. */
    fun hasNotificationPermission(): Boolean {
        return context.hasPostNotificationsPermission()
    }

    /** Cancels any existing alarm for the task and schedules a fresh one if possible. */
    suspend fun rescheduleTask(task: StoredTask) {
        cancelTaskNotifications(task.id)
        if (!canScheduleExactAlarms() || !hasNotificationPermission()) return
        schedule(task, settingsRepository.settingsFlow.first())
    }

    /** Rebuilds alarms for all stored tasks. */
    suspend fun rescheduleAll() {
        val settings = settingsRepository.settingsFlow.first()
        val canScheduleNotifications = canScheduleExactAlarms() && hasNotificationPermission()
        taskDao.getAllTasks().forEach { task ->
            cancelTaskNotifications(task.id)
            if (canScheduleNotifications) {
                schedule(task, settings)
            }
        }
    }

    /** Cancels scheduled notifications for one stored task id. */
    suspend fun cancelTaskNotifications(taskId: Long) {
        TaskReminderType.entries.forEach { reminderType ->
            cancelTaskNotification(taskId, reminderType)
        }
    }

    /** Schedules all enabled exact reminder alarms for a task. */
    private fun schedule(task: StoredTask, settings: AppSettings) {
        val now = System.currentTimeMillis()
        scheduleScheduledReminder(task, settings, now)
        scheduleDueReminder(task, settings, now)
    }

    /** Schedules the TaskNotes scheduled reminder when the setting and value allow it. */
    private fun scheduleScheduledReminder(task: StoredTask, settings: AppSettings, now: Long) {
        val triggerAtMillis = calculateScheduledTriggerAtMillis(task.scheduled, settings, now) ?: return
        scheduleAlarm(task.id, TaskReminderType.SCHEDULED, triggerAtMillis)
    }

    /** Schedules the due date reminder when enabled and the task has a future due date. */
    private fun scheduleDueReminder(task: StoredTask, settings: AppSettings, now: Long) {
        if (!settings.dueDateNotificationsEnabled) return
        val triggerAtMillis = calculateDueTriggerAtMillis(task.due, settings, now) ?: return
        scheduleAlarm(task.id, TaskReminderType.DUE, triggerAtMillis)
    }

    /** Schedules one exact alarm for one task reminder type. */
    private fun scheduleAlarm(taskId: Long, reminderType: TaskReminderType, triggerAtMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(taskId, reminderType, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    /** Cancels the alarm identified by a stored task id. */
    private fun cancelTaskNotification(taskId: Long, reminderType: TaskReminderType) {
        alarmManager.cancel(pendingIntent(taskId, reminderType, PendingIntent.FLAG_UPDATE_CURRENT))
    }

    /** Converts a scheduled TaskNotes value to a future trigger time. */
    private fun calculateScheduledTriggerAtMillis(scheduled: String?, settings: AppSettings, now: Long): Long? {
        if (scheduled.isNullOrBlank()) return null
        val triggerAt = scheduled.toTaskNotesDateTimeEpochMillisOrNull()
            ?.takeIf { settings.scheduledDateTimeNotificationsEnabled }
            ?: calculateDateTriggerAtMillis(
                date = scheduled,
                time = settings.scheduledDateDefaultNotificationTime,
                now = now
            )
            ?: return null
        return triggerAt.takeIf { it > now }
    }

    /** Converts a due TaskNotes value to a future trigger time. */
    private fun calculateDueTriggerAtMillis(due: String?, settings: AppSettings, now: Long): Long? {
        if (due.isNullOrBlank()) return null
        val triggerAt = due.toTaskNotesDateTimeEpochMillisOrNull()
            ?: calculateDateTriggerAtMillis(
                date = due,
                time = settings.dueDateDefaultNotificationTime,
                now = now
            )
            ?: return null
        return triggerAt.takeIf { it > now }
    }

    /** Combines an ISO date with a user-selected notification time. */
    private fun calculateDateTriggerAtMillis(date: String?, time: String, now: Long): Long? {
        if (date.isNullOrBlank()) return null
        val triggerAt = runCatching {
            LocalDateTime.of(
                LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE),
                parseNotificationTime(time)
            ).atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull() ?: return null
        return triggerAt.takeIf { it > now }
    }

    /** Builds the broadcast pending intent used by AlarmManager. */
    private fun pendingIntent(taskId: Long, reminderType: TaskReminderType, flags: Int): PendingIntent {
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = "${context.packageName}.TASK_NOTIFICATION.${reminderType.storageValue}.$taskId"
            putExtra(TaskNotificationReceiver.ExtraTaskId, taskId)
            putExtra(TaskNotificationReceiver.ExtraReminderType, reminderType.storageValue)
        }
        return PendingIntent.getBroadcast(
            context,
            taskReminderStableId(taskId, reminderType),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
