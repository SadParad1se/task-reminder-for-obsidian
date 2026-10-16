package com.sadparad1se.task_reminder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Displays settings for vault selection, scanning, TaskNotes integration, and permissions. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context.applicationContext) }
    val taskRepository = remember { TaskRepository(context.applicationContext) }
    val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scanStates by taskRepository.vaultScanStatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var canScheduleExactAlarms by remember { mutableStateOf(taskRepository.canScheduleExactAlarms()) }
    var hasNotificationPermission by remember { mutableStateOf(taskRepository.hasNotificationPermission()) }

    val selectedVaults = remember(settings.vaultUris) {
        settings.vaultUris.map { vaultUri ->
            val uri = Uri.parse(vaultUri)
            VaultSelection(
                uri = uri,
                displayName = getVaultDisplayName(context, uri)
            )
        }
    }
    val chooseVault = rememberVaultPicker(settings.vaultUris) { vaultUri ->
        settingsRepository.addVaultUri(vaultUri)
        taskRepository.scanVault(vaultUri)
    }
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        canScheduleExactAlarms = taskRepository.canScheduleExactAlarms()
        if (canScheduleExactAlarms) {
            scope.launch { taskRepository.rescheduleAllNotifications() }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || taskRepository.hasNotificationPermission()
        if (hasNotificationPermission) {
            scope.launch { taskRepository.rescheduleAllNotifications() }
        }
    }

    LaunchedEffect(Unit) {
        canScheduleExactAlarms = taskRepository.canScheduleExactAlarms()
        hasNotificationPermission = taskRepository.hasNotificationPermission()
    }

    fun updateReminderSetting(update: suspend () -> Unit) {
        scope.launch {
            update()
            taskRepository.rescheduleAllNotifications()
        }
    }

    SettingsScreenContent(
        settings = settings,
        selectedVaults = selectedVaults,
        scanStates = scanStates,
        canScheduleExactAlarms = canScheduleExactAlarms,
        hasNotificationPermission = hasNotificationPermission,
        onAddVault = chooseVault,
        onRemoveVault = { vaultUri ->
            scope.launch {
                taskRepository.deleteVaultAppData(vaultUri)
                settingsRepository.removeVaultUri(vaultUri)
            }
        },
        onScanNow = { scope.launch { taskRepository.rescanVaults() } },
        onScanFrequencySelected = { frequency ->
            scope.launch { settingsRepository.updateScanFrequency(frequency) }
        },
        onScheduledDateTimeNotificationsChanged = { enabled ->
            updateReminderSetting { settingsRepository.updateScheduledDateTimeNotificationsEnabled(enabled) }
        },
        onScheduledDateDefaultNotificationTimeChanged = { time ->
            updateReminderSetting { settingsRepository.updateScheduledDateDefaultNotificationTime(time) }
        },
        onDueDateNotificationsChanged = { enabled ->
            updateReminderSetting { settingsRepository.updateDueDateNotificationsEnabled(enabled) }
        },
        onDueDateDefaultNotificationTimeChanged = { time ->
            updateReminderSetting { settingsRepository.updateDueDateDefaultNotificationTime(time) }
        },
        onStaleTaskUpdateNotificationsChanged = { enabled ->
            scope.launch { settingsRepository.updateStaleTaskUpdateNotificationsEnabled(enabled) }
        },
        onRequestExactAlarmPermission = {
            exactAlarmLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        },
        onRequestNotificationPermission = {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    )
}
