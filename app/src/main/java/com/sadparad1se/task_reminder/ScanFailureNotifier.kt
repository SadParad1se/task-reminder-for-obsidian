package com.sadparad1se.task_reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Posts a settings notification when vault scanning fails. */
class ScanFailureNotifier(private val context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /** Notifies the user that a vault scan failed and settings may need attention. */
    fun notifyScanFailed() {
        if (!context.hasPostNotificationsPermission()) return
        notificationManager.ensureDefaultChannel(ChannelId, "Scan errors")
        notificationManager.notify(
            NotificationId,
            Notification.Builder(context, ChannelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Vault scan failed")
                .setContentText("Obsidian Notifier could not read one of your vaults. Open settings to fix it.")
                .setContentIntent(settingsPendingIntent())
                .setAutoCancel(true)
                .build()
        )
    }

    /** Builds the settings activity pending intent attached to scan failure notifications. */
    private fun settingsPendingIntent(): PendingIntent {
        val intent = Intent(context, SettingsActivity::class.java)
        return PendingIntent.getActivity(
            context,
            NotificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val ChannelId = "scan_errors"
        const val NotificationId = 1001
    }
}
