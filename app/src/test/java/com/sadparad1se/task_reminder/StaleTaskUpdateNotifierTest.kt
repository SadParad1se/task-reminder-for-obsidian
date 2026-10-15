package com.sadparad1se.task_reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StaleTaskUpdateNotifierTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = StaleTaskUpdateNotifier(context)

    @Before
    fun allowNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager.ensureDefaultChannel(TaskNotifier.ChannelId, "Task notifications")
        manager.ensureDefaultChannel(StaleTaskUpdateNotifier.ChannelId, "Task update reminders")
    }

    private fun notification(channel: String) = Notification.Builder(context, channel)
        .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Reminder").build()

    @Test
    fun renewalAndCancellationPreserveAutomaticAndManualTaskNotifications() {
        val scheduledId = taskReminderStableId(501, TaskReminderType.SCHEDULED)
        assertEquals(1002, scheduledId)
        manager.notify(scheduledId, notification(TaskNotifier.ChannelId))
        manager.notify("manual_task:example", 0, notification(TaskNotifier.ChannelId))

        assertTrue(notifier.notifyNoRecentTaskUpdates())
        assertTrue(notifier.notifyNoRecentTaskUpdates())
        assertEquals(3, manager.activeNotifications.size)
        notifier.cancel()
        assertEquals(2, manager.activeNotifications.size)
        assertTrue(manager.activeNotifications.any { it.tag == null && it.id == scheduledId })
    }

    @Test
    fun removesLegacyInactivityNotificationWithoutLeavingDuplicate() {
        manager.notify(1002, notification(StaleTaskUpdateNotifier.ChannelId))

        notifier.notifyNoRecentTaskUpdates()

        assertEquals(1, manager.activeNotifications.size)
        assertEquals(StaleTaskUpdateNotifier.NotificationTag, manager.activeNotifications.single().tag)
        notifier.cancel()
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    fun cancellationAlsoRemovesLegacyInactivityNotification() {
        manager.notify(1002, notification(StaleTaskUpdateNotifier.ChannelId))
        notifier.cancel()
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    fun fallbackIntentCannotReuseAnOldTaskOpeningRequest() {
        val oldTaskIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java)
                .putExtra(ObsidianProjectOpener.ExtraVaultUri, "old-vault")
                .putExtra(ObsidianProjectOpener.ExtraFilePath, "old-task.md"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notifier.notifyNoRecentTaskUpdates()
        val intent = manager.activeNotifications.single().notification.contentIntent
        assertNotEquals(oldTaskIntent, intent)
        intent.send()
        val launched = shadowOf(context).nextStartedActivity
        assertEquals(MainActivity::class.java.name, launched.component?.className)
        assertFalse(launched.hasExtra(ObsidianProjectOpener.ExtraVaultUri))
        assertFalse(launched.hasExtra(ObsidianProjectOpener.ExtraFilePath))
    }
}
