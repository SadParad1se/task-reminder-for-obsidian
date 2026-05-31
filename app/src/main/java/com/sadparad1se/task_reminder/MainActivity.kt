package com.sadparad1se.task_reminder

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Builds the Compose root and routes between onboarding and the home task list. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TaskReminderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val taskRepository = remember { TaskRepository(applicationContext) }
                    val settingsRepository = remember { SettingsRepository(applicationContext) }
                    val vaultScanWorkScheduler = remember { VaultScanWorkScheduler(applicationContext) }
                    val settings by remember(settingsRepository) {
                        settingsRepository.settingsFlow.map<AppSettings, AppSettings?> { it }
                    }.collectAsStateWithLifecycle(initialValue = null)
                    val priorities by taskRepository.taskPrioritiesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                    val scope = rememberCoroutineScope()
                    var excludedPriorities by remember { mutableStateOf(emptySet<String>()) }
                    var includedTimeBuckets by remember { mutableStateOf(TaskTimeBucket.entries.toSet()) }
                    var canScheduleExactAlarms by remember { mutableStateOf(taskRepository.canScheduleExactAlarms()) }
                    var hasNotificationPermission by remember { mutableStateOf(taskRepository.hasNotificationPermission()) }
                    var onVaultSelectedDuringOnboarding by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var onNotificationPermissionGrantedDuringOnboarding by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var onExactAlarmPermissionGrantedDuringOnboarding by remember { mutableStateOf<(() -> Unit)?>(null) }

                    val loadedSettings = settings ?: return@Surface
                    val selectedVaultName = remember(loadedSettings.vaultUris) {
                        loadedSettings.vaultUris.firstOrNull()
                            ?.let { vaultUri -> getVaultDisplayName(applicationContext, Uri.parse(vaultUri)) }
                    }
                    val taskFlow = remember(excludedPriorities, includedTimeBuckets) {
                        taskRepository.tasksFlow(
                            excludedPriorities = excludedPriorities,
                            includedTimeBuckets = includedTimeBuckets
                        )
                    }
                    val tasks by taskFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                    val priorityColors = remember(tasks) {
                        tasks.mapNotNull { task ->
                            val priority = task.priority ?: return@mapNotNull null
                            priority to task.priorityColor
                        }.toMap()
                    }
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        hasNotificationPermission = granted || taskRepository.hasNotificationPermission()
                        if (hasNotificationPermission) {
                            onNotificationPermissionGrantedDuringOnboarding?.invoke()
                            onNotificationPermissionGrantedDuringOnboarding = null
                        }
                    }
                    val exactAlarmLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) {
                        canScheduleExactAlarms = taskRepository.canScheduleExactAlarms()
                        if (canScheduleExactAlarms) {
                            onExactAlarmPermissionGrantedDuringOnboarding?.invoke()
                            onExactAlarmPermissionGrantedDuringOnboarding = null
                        }
                    }
                    val vaultPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        val uri = result.data?.data
                        if (result.resultCode == Activity.RESULT_OK && uri != null && loadedSettings.vaultUris.none { it == uri.toString() }) {
                            if (!isObsidianVault(applicationContext, uri)) {
                                Toast.makeText(
                                    applicationContext,
                                    InvalidObsidianVaultMessage,
                                    Toast.LENGTH_LONG
                                ).show()
                                return@rememberLauncherForActivityResult
                            }

                            persistVaultReadPermission(applicationContext, uri)
                            scope.launch {
                                settingsRepository.addVaultUri(uri.toString())
                                taskRepository.scanVault(uri.toString())
                                onVaultSelectedDuringOnboarding?.invoke()
                                onVaultSelectedDuringOnboarding = null
                            }
                        }
                    }
                    LaunchedEffect(Unit) {
                        canScheduleExactAlarms = taskRepository.canScheduleExactAlarms()
                        hasNotificationPermission = taskRepository.hasNotificationPermission()
                    }
                    LaunchedEffect(loadedSettings.vaultUris, loadedSettings.scanFrequency) {
                        vaultScanWorkScheduler.schedule(loadedSettings)
                    }
                    if (!loadedSettings.onboardingCompleted) {
                        OnboardingScreen(
                            selectedVaultName = selectedVaultName,
                            hasNotificationPermission = hasNotificationPermission,
                            canScheduleExactAlarms = canScheduleExactAlarms,
                            onChooseVault = { onVaultSelected ->
                                onVaultSelectedDuringOnboarding = onVaultSelected
                                vaultPickerLauncher.launch(createOpenVaultIntent())
                            },
                            onContinue = {
                                scope.launch { settingsRepository.completeOnboarding() }
                            },
                            onRequestNotifications = { onPermissionGranted ->
                                if (hasNotificationPermission || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                                    onPermissionGranted()
                                } else {
                                    onNotificationPermissionGrantedDuringOnboarding = onPermissionGranted
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onOpenExactAlarmSettings = { onPermissionGranted ->
                                if (canScheduleExactAlarms || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                                    onPermissionGranted()
                                } else {
                                    onExactAlarmPermissionGrantedDuringOnboarding = onPermissionGranted
                                    exactAlarmLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                }
                            },
                            onOpenBatterySettings = {
                                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        )
                    } else {
                        HomeScreen(
                            tasks = tasks,
                            priorities = priorities,
                            priorityColors = priorityColors,
                            excludedPriorities = excludedPriorities,
                            includedTimeBuckets = includedTimeBuckets,
                            onPriorityFilterToggle = { priority ->
                                excludedPriorities = if (priority in excludedPriorities) {
                                    excludedPriorities - priority
                                } else {
                                    excludedPriorities + priority
                                }
                            },
                            onTimeBucketToggle = { bucket ->
                                includedTimeBuckets = if (bucket in includedTimeBuckets) {
                                    includedTimeBuckets - bucket
                                } else {
                                    includedTimeBuckets + bucket
                                }
                            },
                            onTaskClick = { task ->
                                lifecycleScope.launch {
                                    ObsidianProjectOpener.openTaskNotes(this@MainActivity, task)
                                }
                            },
                            onOpenSettings = {
                                startActivity(Intent(this, SettingsActivity::class.java))
                            }
                        )
                    }
                }
            }
        }
        handleTaskNotesOpenIntent(intent)
    }

    /** Handles new intents delivered while the existing activity instance is reused. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTaskNotesOpenIntent(intent)
    }

    /** Opens the task file when a notification launches the app with vault metadata. */
    private fun handleTaskNotesOpenIntent(intent: Intent) {
        ObsidianProjectOpener.targetFromIntent(intent)?.let { target ->
            lifecycleScope.launch {
                ObsidianProjectOpener.openTaskNotes(this@MainActivity, target, showToast = false)
            }
        }
    }

}
