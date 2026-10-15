package com.sadparad1se.task_reminder

import android.content.Context
import android.widget.Toast

/** Success belongs to the task's bell; failures retain their explanatory messages. */
internal fun Context.showTaskNotificationFeedback(
    fileUri: String,
    result: TaskNotificationResult,
    onPosted: (String) -> Unit
) {
    when (result) {
        TaskNotificationResult.POSTED -> onPosted(fileUri)
        TaskNotificationResult.DISABLED -> Toast.makeText(this, R.string.task_notifications_disabled, Toast.LENGTH_SHORT).show()
        TaskNotificationResult.TASK_NOT_FOUND -> Toast.makeText(this, R.string.task_notification_missing, Toast.LENGTH_SHORT).show()
    }
}
