package com.sadparad1se.task_reminder

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Stateless settings UI used by both the real screen and previews. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScreenContent(
    settings: AppSettings,
    selectedVaults: List<VaultSelection>,
    scanStates: List<VaultScanState>,
    isScanning: Boolean,
    canScheduleExactAlarms: Boolean,
    hasNotificationPermission: Boolean,
    onAddVault: () -> Unit,
    onRemoveVault: (String) -> Unit,
    onChooseConfigurationFolder: (String) -> Unit,
    onScanNow: () -> Unit,
    onScanFrequencySelected: (ScanFrequency) -> Unit,
    onScheduledDateTimeNotificationsChanged: (Boolean) -> Unit,
    onScheduledDateDefaultNotificationTimeChanged: (String) -> Unit,
    onDueDateNotificationsChanged: (Boolean) -> Unit,
    onDueDateDefaultNotificationTimeChanged: (String) -> Unit,
    onStaleTaskUpdateNotificationsChanged: (Boolean) -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    Scaffold(
        modifier = Modifier.background(appBackgroundBrush(0.84f)),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            vaultSection(selectedVaults, onRemoveVault, onChooseConfigurationFolder, enabled = !isScanning)
            item {
                VaultActionRow(
                    scanEnabled = settings.vaultUris.isNotEmpty(),
                    isScanning = isScanning,
                    onAddVault = onAddVault,
                    onScanNow = onScanNow
                )
            }
            scanStatusSection(selectedVaults, scanStates)
            scanFrequencySection(settings.scanFrequency, onScanFrequencySelected)
            permissionsSection(
                canScheduleExactAlarms = canScheduleExactAlarms,
                hasNotificationPermission = hasNotificationPermission,
                onRequestExactAlarmPermission = onRequestExactAlarmPermission,
                onRequestNotificationPermission = onRequestNotificationPermission
            )
            notificationSection(
                settings = settings,
                onScheduledDateTimeNotificationsChanged = onScheduledDateTimeNotificationsChanged,
                onScheduledDateDefaultNotificationTimeChanged = onScheduledDateDefaultNotificationTimeChanged,
                onDueDateNotificationsChanged = onDueDateNotificationsChanged,
                onDueDateDefaultNotificationTimeChanged = onDueDateDefaultNotificationTimeChanged,
                onStaleTaskUpdateNotificationsChanged = onStaleTaskUpdateNotificationsChanged
            )
        }
    }
}

/** Displays a section heading within the settings list. */
@Composable
internal fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary
    )
}

/** Renders the settings screen in Android Studio preview. */
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    TaskReminderTheme {
        SettingsScreenContent(
            settings = AppSettings(vaultUris = listOf("content://preview/vault")),
            selectedVaults = listOf(VaultSelection(Uri.parse("content://preview/vault"), "Preview Vault")),
            scanStates = emptyList(),
            isScanning = false,
            canScheduleExactAlarms = true,
            hasNotificationPermission = true,
            onAddVault = {},
            onRemoveVault = {},
            onChooseConfigurationFolder = {},
            onScanNow = {},
            onScanFrequencySelected = {},
            onScheduledDateTimeNotificationsChanged = {},
            onScheduledDateDefaultNotificationTimeChanged = {},
            onDueDateNotificationsChanged = {},
            onDueDateDefaultNotificationTimeChanged = {},
            onStaleTaskUpdateNotificationsChanged = {},
            onRequestExactAlarmPermission = {},
            onRequestNotificationPermission = {}
        )
    }
}
