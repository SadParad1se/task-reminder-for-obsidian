package com.sadparad1se.task_reminder

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
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
    fun setupActionsAdvanceOnlyAfterTheirCompletionCallbacks() {
        val calls = mutableListOf<String>()
        lateinit var completeAction: () -> Unit
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            controller.get().setContent {
                TaskReminderTheme {
                    OnboardingScreen(
                        selectedVaultName = null,
                        hasNotificationPermission = false,
                        canScheduleExactAlarms = false,
                        onChooseVault = {
                            calls.add("vault")
                            completeAction = it
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
            repeat(2) { compose.onNodeWithText("Next").performClick() }
            for (label in listOf("Choose Obsidian vault", "Allow notifications", "Allow exact alarms")) {
                compose.onNodeWithText(label).performClick().assertIsDisplayed()
                compose.runOnIdle { completeAction() }
            }
            compose.onNodeWithText("Open battery settings").performClick().assertIsDisplayed()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Get started").performClick()
            assertEquals(listOf("vault", "notifications", "alarms", "battery", "continue"), calls)
        }
    }
}
