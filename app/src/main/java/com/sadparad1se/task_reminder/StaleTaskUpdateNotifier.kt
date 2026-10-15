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
    fun notifyNoRecentTaskUpdates(): Boolean {
        if (!context.hasPostNotificationsPermission() || !notificationManager.areNotificationsEnabled()) return false
        notificationManager.ensureDefaultChannel(ChannelId, "Task update reminders")
        if (notificationManager.getNotificationChannel(ChannelId)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        return try {
            removeLegacyNotification()
            notificationManager.notify(
                NotificationTag,
                NotificationId,
                Notification.Builder(context, ChannelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Open Obsidian")
                    .setContentText("No tasks have been updated in the last 24 hours. Open Obsidian to review your tasks.")
                    .setContentIntent(openObsidianPendingIntent())
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(false)
                    .build()
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** Removes only this reminder, preserving task notifications with overlapping numeric ids. */
    fun cancel() {
        notificationManager.cancel(NotificationTag, NotificationId)
        removeLegacyNotification()
    }

    private fun removeLegacyNotification() {
        if (notificationManager.activeNotifications.any {
            it.tag == null && it.id == LegacyNotificationId && it.notification.channelId == ChannelId
        }) {
            notificationManager.cancel(LegacyNotificationId)
        }
    }

    /** Builds a pending intent that opens Obsidian, falling back to this app. */
    private fun openObsidianPendingIntent(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(ObsidianPackageName)
            ?: Intent(context, MainActivity::class.java)
        intent.identifier = "${context.packageName}.STALE_TASK_UPDATES"
        return PendingIntent.getActivity(
            context,
            NotificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal companion object {
        const val ChannelId = "task_update_reminders"
        const val NotificationTag = "stale_task_updates"
        const val NotificationId = 0
        const val LegacyNotificationId = 1002
    }
}
