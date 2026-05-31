package com.sadparad1se.task_reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives scheduled alarm broadcasts and displays task reminder notifications. */
class TaskNotificationReceiver : BroadcastReceiver() {
    /** Loads the target task and posts its reminder notification. */
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.hasPostNotificationsPermission()) return
        val taskId = intent.getLongExtra(ExtraTaskId, 0L)
        if (taskId <= 0L) return
        val reminderType = TaskReminderType.fromStorageValue(intent.getStringExtra(ExtraReminderType))
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val database = TaskDatabase.getInstance(context.applicationContext)
                val task = database.taskDao().getTask(taskId) ?: return@launch
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.ensureDefaultChannel(ChannelId, "Task notifications")
                notificationManager.notify(
                    taskReminderStableId(taskId, reminderType),
                    buildNotification(context, task, reminderType)
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Builds the Android notification shown for one scheduled task. */
    private fun buildNotification(
        context: Context,
        task: StoredTask,
        reminderType: TaskReminderType
    ): Notification {
        val details = buildString {
            task.scheduled?.let { append("Scheduled: $it") }
            task.due?.let {
                if (isNotBlank()) append("\n")
                append("Due: $it")
            }
            if (task.excerpt.isNotBlank()) append("\n${task.excerpt}")
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            ObsidianProjectOpener.createTaskOpenIntent(context, task),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(context, ChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(task.title)
            .setContentText(notificationSummary(task, reminderType))
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    }

    /** Returns the short text shown in the collapsed notification. */
    private fun notificationSummary(task: StoredTask, reminderType: TaskReminderType): String {
        return when (reminderType) {
            TaskReminderType.SCHEDULED -> task.scheduled?.let { "Scheduled: $it" } ?: "Scheduled task reminder"
            TaskReminderType.DUE -> task.due?.let { "Due: $it" } ?: "Due task reminder"
        }
    }

    companion object {
        const val ExtraTaskId = "task_id"
        const val ExtraReminderType = "reminder_type"
        private const val ChannelId = "task_notifications"
    }
}
