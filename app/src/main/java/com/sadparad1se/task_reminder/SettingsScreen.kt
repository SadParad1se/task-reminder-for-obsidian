package com.sadparad1se.task_reminder

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

/** Displays settings for vault selection, scanning, TaskNotes integration, and permissions. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen() {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context.applicationContext) }
    val taskRepository = remember { TaskRepository(context.applicationContext) }
    val settings by remember(settingsRepository) {
        settingsRepository.settingsFlow.map<AppSettings, AppSettings?> { it }
    }.collectAsStateWithLifecycle(initialValue = null)
    val scanStates by taskRepository.vaultScanStatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var canScheduleExactAlarms by remember { mutableStateOf(taskRepository.canScheduleExactAlarms()) }
    var hasNotificationPermission by remember { mutableStateOf(taskRepository.hasNotificationPermission()) }

    val loadedSettings = settings ?: return
    val selectedVaults = remember(loadedSettings.vaultUris) {
        loadedSettings.vaultUris.map { vaultUri ->
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
        if (result.resultCode == Activity.RESULT_OK && uri != null && loadedSettings.vaultUris.none { it == uri.toString() }) {
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
        settings = loadedSettings,
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
        onScanNow = { scope.launch { taskRepository.scanVaults(loadedSettings.vaultUris) } },
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

/** Stateless settings UI used by both the real screen and previews. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsScreenContent(
    settings: AppSettings,
    selectedVaults: List<VaultSelection>,
    scanStates: List<VaultScanState>,
    canScheduleExactAlarms: Boolean,
    hasNotificationPermission: Boolean,
    onAddVault: () -> Unit,
    onRemoveVault: (String) -> Unit,
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
        modifier = Modifier.background(settingsBackgroundBrush()),
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
            vaultSection(selectedVaults, onRemoveVault)
            item {
                VaultActionRow(
                    scanEnabled = settings.vaultUris.isNotEmpty(),
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

/** Adds the selected vault list section to the settings screen. */
private fun LazyListScope.vaultSection(
    selectedVaults: List<VaultSelection>,
    onRemoveVault: (String) -> Unit
) {
    item { SectionTitle("Obsidian vaults") }

    if (selectedVaults.isEmpty()) {
        item {
            Text(
                text = "No vaults selected yet.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        items(selectedVaults) { vault ->
            VaultRow(vault, onRemoveVault)
        }
    }
}

/** Displays one selected vault and lets the user remove its stored data. */
@Composable
private fun VaultRow(
    vault: VaultSelection,
    onRemoveVault: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = vault.displayName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    onRemoveVault(vault.uri.toString())
                }
            ) {
                Text("Remove")
            }
        }
    }
}

/** Displays vault-level actions side by side. */
@Composable
private fun VaultActionRow(
    scanEnabled: Boolean,
    onAddVault: () -> Unit,
    onScanNow: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onAddVault,
            modifier = Modifier.weight(1f)
        ) {
            Text("Add Vault")
        }
        Button(
            onClick = onScanNow,
            enabled = scanEnabled,
            modifier = Modifier.weight(1f)
        ) {
            Text("Scan now")
        }
    }
}

/** Adds current scan statuses to the settings screen. */
private fun LazyListScope.scanStatusSection(
    selectedVaults: List<VaultSelection>,
    scanStates: List<VaultScanState>
) {
    item {
        ScanStatusList(
            vaults = selectedVaults,
            scanStates = scanStates
        )
    }
}

/** Adds scan frequency choices to the settings screen. */
private fun LazyListScope.scanFrequencySection(
    selectedFrequency: ScanFrequency,
    onFrequencySelected: (ScanFrequency) -> Unit
) {
    val frequencies = ScanFrequency.entries.toList()
    item { SectionTitle("Scan frequency") }
    item {
        ScanFrequencySlider(
            frequencies = frequencies,
            selectedFrequency = selectedFrequency,
            onFrequencySelected = onFrequencySelected
        )
    }
}

/** Displays scan frequency choices as a slider and persists only after release. */
@Composable
private fun ScanFrequencySlider(
    frequencies: List<ScanFrequency>,
    selectedFrequency: ScanFrequency,
    onFrequencySelected: (ScanFrequency) -> Unit
) {
    val selectedIndex = frequencies.indexOf(selectedFrequency).coerceAtLeast(0)
    var sliderValue by remember(selectedFrequency) {
        mutableFloatStateOf(selectedIndex.toFloat())
    }
    val selectedSliderIndex = sliderValue
        .roundToInt()
        .coerceIn(0, frequencies.lastIndex)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Scan every: ${frequencies[selectedSliderIndex].label}",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            valueRange = 0f..frequencies.lastIndex.toFloat(),
            steps = frequencies.size - 2,
            onValueChangeFinished = {
                onFrequencySelected(frequencies[selectedSliderIndex])
            }
        )
    }
}

/** Adds platform permission guidance to the settings screen. */
private fun LazyListScope.permissionsSection(
    canScheduleExactAlarms: Boolean,
    hasNotificationPermission: Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    item { SectionTitle("Permissions") }
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

/** Adds notification preference controls to the settings screen. */
private fun LazyListScope.notificationSection(
    settings: AppSettings,
    onScheduledDateTimeNotificationsChanged: (Boolean) -> Unit,
    onScheduledDateDefaultNotificationTimeChanged: (String) -> Unit,
    onDueDateNotificationsChanged: (Boolean) -> Unit,
    onDueDateDefaultNotificationTimeChanged: (String) -> Unit,
    onStaleTaskUpdateNotificationsChanged: (Boolean) -> Unit
) {
    item { SectionTitle("Notifications") }

    item {
        NotificationToggleRow(
            label = "Scheduled date-time notifications",
            checked = settings.scheduledDateTimeNotificationsEnabled,
            onCheckedChange = onScheduledDateTimeNotificationsChanged
        )
    }

    item {
        val context = LocalContext.current
        NotificationTimeRow(
            label = "Scheduled date-only notification time",
            time = settings.scheduledDateDefaultNotificationTime,
            onClick = {
                showNotificationTimePicker(context, settings.scheduledDateDefaultNotificationTime, onScheduledDateDefaultNotificationTimeChanged)
            }
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
        val context = LocalContext.current
        NotificationTimeRow(
            label = "Due date notification time",
            time = settings.dueDateDefaultNotificationTime,
            onClick = {
                showNotificationTimePicker(context, settings.dueDateDefaultNotificationTime, onDueDateDefaultNotificationTimeChanged)
            }
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
    onClick: () -> Unit
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
        TextButton(onClick = onClick) {
            Text(time)
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

/** Displays a section heading within the settings list. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary
    )
}

/** Displays per-vault scan status messages. */
@Composable
private fun ScanStatusList(
    vaults: List<VaultSelection>,
    scanStates: List<VaultScanState>
) {
    if (vaults.isEmpty()) return

    val scanStateByVault = scanStates.associateBy { it.vaultUri }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        vaults.forEach { vault ->
            val scanState = scanStateByVault[vault.uri.toString()]
            Text(
                text = "${vault.displayName}: ${formatScanStatus(scanState)}",
                style = MaterialTheme.typography.bodySmall
            )
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

/** Renders the settings screen in Android Studio preview. */
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    TaskReminderTheme {
        SettingsScreenContent(
            settings = AppSettings(vaultUris = listOf("content://preview/vault")),
            selectedVaults = listOf(VaultSelection(Uri.parse("content://preview/vault"), "Preview Vault")),
            scanStates = emptyList(),
            canScheduleExactAlarms = true,
            hasNotificationPermission = true,
            onAddVault = {},
            onRemoveVault = {},
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

/** Creates the same neon-tinted backdrop used by the rest of the app. */
@Composable
private fun settingsBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.84f),
            MaterialTheme.colorScheme.background
        )
    )
}
