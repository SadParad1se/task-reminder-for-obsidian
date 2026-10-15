package com.sadparad1se.task_reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.net.Uri

/** Result of an immediate attempt to post a task notification. */
internal enum class TaskNotificationResult {
    POSTED,
    DISABLED,
    TASK_NOT_FOUND
}

/** Shares notification content and note-opening behavior between alarms and manual requests. */
internal class TaskNotifier(private val context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /** Creates or refreshes one manual notification without changing automatic reminders. */
    fun postManual(task: StoredTask): TaskNotificationResult {
        if (!canPost()) return TaskNotificationResult.DISABLED
        val summary = task.scheduled?.let { "Scheduled: $it" }
            ?: task.due?.let { "Due: $it" }
            ?: task.excerpt.takeIf { it.isNotBlank() }
            ?: "Task reminder"
        val intent = ObsidianProjectOpener.createTaskOpenIntent(context, task).apply {
            action = "${context.packageName}.MANUAL_TASK_NOTIFICATION"
            data = Uri.parse(task.fileUri)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return post {
            notificationManager.notify("manual_task:${task.fileUri}", 0, buildNotification(task, summary, contentIntent))
        }
    }

    /** Posts an automatic reminder using its existing identity and date-specific summary. */
    fun postScheduled(task: StoredTask, reminderType: TaskReminderType): TaskNotificationResult {
        if (!canPost()) return TaskNotificationResult.DISABLED
        val summary = when (reminderType) {
            TaskReminderType.SCHEDULED -> task.scheduled?.let { "Scheduled: $it" } ?: "Scheduled task reminder"
            TaskReminderType.DUE -> task.due?.let { "Due: $it" } ?: "Due task reminder"
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            ObsidianProjectOpener.createTaskOpenIntent(context, task),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return post {
            notificationManager.notify(taskReminderStableId(task.id, reminderType), buildNotification(task, summary, contentIntent))
        }
    }

    private fun canPost(): Boolean {
        if (!context.hasPostNotificationsPermission() || !notificationManager.areNotificationsEnabled()) return false
        notificationManager.ensureDefaultChannel(ChannelId, "Task notifications")
        return notificationManager.getNotificationChannel(ChannelId)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Handles permission changes that race with the initial availability check. */
    private inline fun post(notify: () -> Unit): TaskNotificationResult {
        return try {
            notify()
            TaskNotificationResult.POSTED
        } catch (_: SecurityException) {
            TaskNotificationResult.DISABLED
        }
    }

    private fun buildNotification(task: StoredTask, summary: String, contentIntent: PendingIntent): Notification {
        val details = listOfNotNull(
            task.scheduled?.let { "Scheduled: $it" },
            task.due?.let { "Due: $it" },
            task.excerpt.takeIf { it.isNotBlank() }
        ).joinToString("\n")
        return Notification.Builder(context, ChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(task.title)
            .setContentText(summary)
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    }

    internal companion object {
        const val ChannelId = "task_notifications"
    }
}
