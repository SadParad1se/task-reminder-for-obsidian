package com.sadparad1se.task_reminder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
    val vaultPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && settings.vaultUris.none { it == uri.toString() }) {
            if (!isObsidianVault(context, uri)) {
                Toast.makeText(
                    context,
                    InvalidObsidianVaultMessage,
                    Toast.LENGTH_LONG
                ).show()
                return@rememberLauncherForActivityResult
            }

            persistVaultReadPermission(context, uri)
            scope.launch {
                settingsRepository.addVaultUri(uri.toString())
                taskRepository.scanVault(uri.toString())
            }
        }
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

    SettingsScreenContent(
        settings = settings,
        selectedVaults = selectedVaults,
        scanStates = scanStates,
        canScheduleExactAlarms = canScheduleExactAlarms,
        hasNotificationPermission = hasNotificationPermission,
        onAddVault = { vaultPickerLauncher.launch(createOpenVaultIntent()) },
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
            scope.launch {
                settingsRepository.updateScheduledDateTimeNotificationsEnabled(enabled)
                taskRepository.rescheduleAllNotifications()
            }
        },
        onScheduledDateDefaultNotificationTimeChanged = { time ->
            scope.launch {
                settingsRepository.updateScheduledDateDefaultNotificationTime(time)
                taskRepository.rescheduleAllNotifications()
            }
        },
        onDueDateNotificationsChanged = { enabled ->
            scope.launch {
                settingsRepository.updateDueDateNotificationsEnabled(enabled)
                taskRepository.rescheduleAllNotifications()
            }
        },
        onDueDateDefaultNotificationTimeChanged = { time ->
            scope.launch {
                settingsRepository.updateDueDateDefaultNotificationTime(time)
                taskRepository.rescheduleAllNotifications()
            }
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
