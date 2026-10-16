package com.sadparad1se.task_reminder

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.LocalTime

/** Adds notification preference controls to the settings screen. */
internal fun LazyListScope.notificationSection(
    settings: AppSettings,
    onScheduledDateTimeNotificationsChanged: (Boolean) -> Unit,
    onScheduledDateDefaultNotificationTimeChanged: (String) -> Unit,
    onDueDateNotificationsChanged: (Boolean) -> Unit,
    onDueDateDefaultNotificationTimeChanged: (String) -> Unit,
    onStaleTaskUpdateNotificationsChanged: (Boolean) -> Unit
) {
    item { SettingsSectionTitle("Notifications") }

    item {
        NotificationToggleRow(
            label = "Scheduled date-time notifications",
            checked = settings.scheduledDateTimeNotificationsEnabled,
            onCheckedChange = onScheduledDateTimeNotificationsChanged
        )
    }

    item {
        NotificationTimeRow(
            label = "Scheduled date-only notification time",
            time = settings.scheduledDateDefaultNotificationTime,
            onTimeSelected = onScheduledDateDefaultNotificationTimeChanged
        )
    }

    item {
        NotificationToggleRow(
            label = "Due date notifications",
            checked = settings.dueDateNotificationsEnabled,
            onCheckedChange = onDueDateNotificationsChanged
        )
    }

    item {
        NotificationTimeRow(
            label = "Due date notification time",
            time = settings.dueDateDefaultNotificationTime,
            onTimeSelected = onDueDateDefaultNotificationTimeChanged
        )
    }

    item {
        NotificationToggleRow(
            label = "Notify me, if no tasks were updated in the last 24 hours",
            checked = settings.staleTaskUpdateNotificationsEnabled,
            onCheckedChange = onStaleTaskUpdateNotificationsChanged
        )
    }

}

/** Displays one notification preference toggle. */
@Composable
private fun NotificationToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/** Displays one notification time setting row. */
@Composable
private fun NotificationTimeRow(
    label: String,
    time: String,
    onTimeSelected: (String) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { showNotificationTimePicker(context, time, onTimeSelected) }) {
            Text(time)
        }
    }
}

/** Opens a platform time picker and returns the selected time as HH:mm. */
private fun showNotificationTimePicker(
    context: Context,
    currentTime: String,
    onTimeSelected: (String) -> Unit
) {
    val parsedTime = parseNotificationTime(currentTime)
    TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            onTimeSelected(LocalTime.of(hourOfDay, minute).format(NotificationTimeFormatter))
        },
        parsedTime.hour,
        parsedTime.minute,
        true
    ).show()
}
