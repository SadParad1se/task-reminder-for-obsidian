package com.sadparad1se.task_reminder

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Adds platform permission guidance to the settings screen. */
internal fun LazyListScope.permissionsSection(
    canScheduleExactAlarms: Boolean,
    hasNotificationPermission: Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    item { SettingsSectionTitle("Permissions") }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PermissionStatusRow(
                label = "Notifications",
                enabled = hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
                buttonText = "Enable",
                showButton = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                onClick = onRequestNotificationPermission
            )
            PermissionStatusRow(
                label = "Exact alarms",
                enabled = canScheduleExactAlarms,
                buttonText = "Enable",
                showButton = true,
                onClick = onRequestExactAlarmPermission
            )
            BatterySettingsButton()
        }
    }
}

/** Shows one platform permission status row with an enable action when disabled. */
@Composable
private fun PermissionStatusRow(
    label: String,
    enabled: Boolean,
    buttonText: String,
    showButton: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ${if (enabled) "enabled" else "disabled"}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (!enabled && showButton) {
            Button(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}

/** Displays the battery optimization settings shortcut and explanation. */
@Composable
private fun BatterySettingsButton() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        ) {
            Text("Open battery optimization settings")
        }
        Text(
            text = "Disable battery optimization for more reliable scans and notifications.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
