package com.sadparad1se.task_reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Posts a reminder when scanned tasks have not been updated recently. */
class StaleTaskUpdateNotifier(private val context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /** Notifies the user to open Obsidian and review stale task data. */
    fun notifyNoRecentTaskUpdates() {
        if (!context.hasPostNotificationsPermission()) return
        notificationManager.ensureDefaultChannel(ChannelId, "Task update reminders")
        notificationManager.notify(
            NotificationId,
            Notification.Builder(context, ChannelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Open Obsidian")
                .setContentText("No tasks have been updated in the last 24 hours. Open Obsidian to review your tasks.")
                .setContentIntent(openObsidianPendingIntent())
                .setAutoCancel(true)
                .build()
        )
    }

    /** Builds a pending intent that opens Obsidian, falling back to this app. */
    private fun openObsidianPendingIntent(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(ObsidianPackageName)
            ?: Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            NotificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val ChannelId = "task_update_reminders"
        const val NotificationId = 1002
    }
}
