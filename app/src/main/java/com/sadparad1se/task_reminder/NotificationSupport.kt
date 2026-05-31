package com.sadparad1se.task_reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Returns whether Android allows this app to post notifications. */
fun Context.hasPostNotificationsPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

/** Creates a default-importance notification channel on Android O and newer. */
fun NotificationManager.ensureDefaultChannel(
    id: String,
    name: String
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    createNotificationChannel(
        NotificationChannel(
            id,
            name,
            NotificationManager.IMPORTANCE_DEFAULT
        )
    )
}
