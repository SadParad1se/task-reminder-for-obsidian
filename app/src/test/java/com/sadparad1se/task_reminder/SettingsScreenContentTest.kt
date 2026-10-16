package com.sadparad1se.task_reminder

import android.app.TimePickerDialog
import android.content.DialogInterface
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class SettingsScreenContentTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private val calls = mutableListOf<String>()
    private val vault = VaultSelection(Uri.parse("content://vault/example"), "Example")

    @Test
    fun vaultActionsKeepTheSelectedVaultIdentity() = withSettings(listOf(vault)) {
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Add Vault").performClick()
        compose.onNodeWithText("Scan now").performClick()
        assertEquals(listOf("remove:${vault.uri}", "add", "scan"), calls)
    }

    @Test
    fun scanningIsDisabledWithoutVaults() = withSettings(emptyList()) {
        compose.onNodeWithText("Scan now").assertIsNotEnabled()
    }

    @Test
    fun notificationControlsReturnTheNewToggleAndTimeValues() = withSettings(listOf(vault)) {
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Scheduled date-time notifications"))
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Scheduled date-only notification time"))
        compose.onAllNodesWithText("07:00").onFirst().performClick()
        compose.runOnIdle {
            val dialog = ShadowAlertDialog.getLatestAlertDialog() as TimePickerDialog
            dialog.updateTime(9, 45)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        assertEquals(listOf("scheduled:false", "time:09:45"), calls)
    }

    private fun withSettings(vaults: List<VaultSelection>, check: () -> Unit) {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            controller.get().setContent {
                TaskReminderTheme {
                    SettingsScreenContent(
                        settings = AppSettings(vaultUris = vaults.map { it.uri.toString() }),
                        selectedVaults = vaults,
                        scanStates = emptyList(),
                        canScheduleExactAlarms = true,
                        hasNotificationPermission = true,
                        onAddVault = { calls.add("add") },
                        onRemoveVault = { calls.add("remove:$it") },
                        onScanNow = { calls.add("scan") },
                        onScanFrequencySelected = {},
                        onScheduledDateTimeNotificationsChanged = { calls.add("scheduled:$it") },
                        onScheduledDateDefaultNotificationTimeChanged = { calls.add("time:$it") },
                        onDueDateNotificationsChanged = {},
                        onDueDateDefaultNotificationTimeChanged = {},
                        onStaleTaskUpdateNotificationsChanged = {},
                        onRequestExactAlarmPermission = {},
                        onRequestNotificationPermission = {}
                    )
                }
            }
            check()
        }
    }
}
