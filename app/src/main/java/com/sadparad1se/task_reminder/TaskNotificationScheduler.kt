package com.sadparad1se.task_reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.first
import java.time.ZoneId

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
        val zone = ZoneId.systemDefault()
        scheduledReminderTimeMillis(task.scheduled, settings, now, zone)?.let { triggerAt ->
            scheduleAlarm(task.id, TaskReminderType.SCHEDULED, triggerAt)
        }
        dueReminderTimeMillis(task.due, settings, now, zone)?.let { triggerAt ->
            scheduleAlarm(task.id, TaskReminderType.DUE, triggerAt)
        }
    }

    /** Schedules one exact alarm for one task reminder type. */
    private fun scheduleAlarm(taskId: Long, reminderType: TaskReminderType, triggerAtMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(taskId, reminderType)
        )
    }

    /** Cancels the alarm identified by a stored task id. */
    private fun cancelTaskNotification(taskId: Long, reminderType: TaskReminderType) {
        alarmManager.cancel(pendingIntent(taskId, reminderType))
    }

    /** Builds the broadcast pending intent used by AlarmManager. */
    private fun pendingIntent(taskId: Long, reminderType: TaskReminderType): PendingIntent {
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = "${context.packageName}.TASK_NOTIFICATION.${reminderType.storageValue}.$taskId"
            putExtra(TaskNotificationReceiver.ExtraTaskId, taskId)
            putExtra(TaskNotificationReceiver.ExtraReminderType, reminderType.storageValue)
        }
        return PendingIntent.getBroadcast(
            context,
            taskReminderStableId(taskId, reminderType),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
