package com.sadparad1se.task_reminder

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
                TaskNotifier(context).postScheduled(task, reminderType)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ExtraTaskId = "task_id"
        const val ExtraReminderType = "reminder_type"
    }
}
