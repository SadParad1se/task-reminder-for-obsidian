package com.sadparad1se.task_reminder

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h1000dp")
class OnboardingScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun vaultSelectionStaysOnPageWhilePermissionActionsAdvanceAfterCompletion() {
        val calls = mutableListOf<String>()
        val vaultName = mutableStateOf<String?>(null)
        val configurationFolder = mutableStateOf(".obsidian")
        val isScanning = mutableStateOf(false)
        lateinit var completeAction: () -> Unit
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            controller.get().setContent {
                TaskReminderTheme {
                    OnboardingScreen(
                        selectedVaultName = vaultName.value,
                        selectedConfigurationFolder = configurationFolder.value,
                        isScanning = isScanning.value,
                        hasNotificationPermission = false,
                        canScheduleExactAlarms = false,
                        onChooseVault = {
                            calls.add("vault")
                            vaultName.value = "Example vault"
                        },
                        onChooseConfigurationFolder = {
                            calls.add("configuration")
                            configurationFolder.value = ".obsidianHandy1"
                            isScanning.value = true
                        },
                        onContinue = { calls.add("continue") },
                        onRequestNotifications = {
                            calls.add("notifications")
                            completeAction = it
                        },
                        onOpenExactAlarmSettings = {
                            calls.add("alarms")
                            completeAction = it
                        },
                        onOpenBatterySettings = { calls.add("battery") }
                    )
                }
            }
            compose.onNodeWithText("Configuration folder: .obsidian").assertDoesNotExist()
            repeat(2) { compose.onNodeWithText("Next").performClick() }
            compose.onNodeWithText("Configuration folder: .obsidian").assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithText("Choose Obsidian vault").performClick().assertIsDisplayed()
            compose.onNodeWithText("Vault selected: Example vault").assertIsDisplayed()
            compose.onNodeWithText("Configuration folder: .obsidian").assertIsEnabled().performClick()
            compose.onNodeWithText("Configuration folder: .obsidianHandy1").assertIsDisplayed()
            compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)
                .assertIsDisplayed()
            compose.onNodeWithText("Choose Obsidian vault").assertIsNotEnabled()
            compose.onNodeWithText("Configuration folder: .obsidianHandy1").assertIsNotEnabled()
            compose.onNodeWithText("Next").assertIsEnabled()
            compose.runOnIdle { isScanning.value = false }
            compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)
                .assertDoesNotExist()
            compose.onNodeWithText("Configuration folder: .obsidianHandy1").assertIsEnabled()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Configuration folder: .obsidianHandy1").assertIsNotDisplayed()
            for (label in listOf("Allow notifications", "Allow exact alarms")) {
                compose.onNodeWithText(label).performClick().assertIsDisplayed()
                compose.runOnIdle { completeAction() }
            }
            compose.onNodeWithText("Open battery settings").performClick().assertIsDisplayed()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Get started").performClick()
            assertEquals(listOf("vault", "configuration", "notifications", "alarms", "battery", "continue"), calls)
        }
    }
}
