package com.sadparad1se.task_reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Rebuilds exact task reminder alarms after Android finishes booting. */
class BootCompletedReceiver : BroadcastReceiver() {
    /** Handles boot completion by rescheduling all persisted task notifications. */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TaskRepository(context.applicationContext).rescheduleAllNotifications()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
