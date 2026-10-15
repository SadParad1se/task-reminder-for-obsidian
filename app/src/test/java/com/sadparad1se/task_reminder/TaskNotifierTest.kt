package com.sadparad1se.task_reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskNotifierTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = TaskNotifier(context)

    @Before
    fun allowNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun postsTaskDetailsWithScheduledSummary() {
        val task = task()

        assertEquals(TaskNotificationResult.POSTED, notifier.postManual(task))

        val notification = manager.activeNotifications.single().notification
        assertEquals("Task notifications", manager.getNotificationChannel(TaskNotifier.ChannelId).name)
        assertEquals(task.title, notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("Scheduled: ${task.scheduled}", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(
            "Scheduled: ${task.scheduled}\nDue: ${task.due}\n${task.excerpt}",
            notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        )
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertFalse(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun usesDueThenExcerptThenGenericSummaryForUndatedTasks() {
        val task = task().copy(scheduled = null)
        val cases = listOf(
            task to "Due: ${task.due}",
            task.copy(due = null) to task.excerpt,
            task.copy(due = null, excerpt = " ") to "Task reminder"
        )
        cases.forEach { (candidate, summary) ->
            assertEquals(TaskNotificationResult.POSTED, notifier.postManual(candidate))
            assertEquals(summary, manager.activeNotifications.single().notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        }
    }

    @Test
    @Config(sdk = [30, 33, 36])
    fun manualPostsAreSilentWithoutChangingChannelSettings() {
        val sound = Settings.System.DEFAULT_NOTIFICATION_URI
        val vibration = longArrayOf(0, 100, 100, 100)
        manager.createNotificationChannel(
            NotificationChannel(TaskNotifier.ChannelId, "Task notifications", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                vibrationPattern = vibration
                enableVibration(true)
            }
        )

        assertEquals(TaskNotificationResult.POSTED, notifier.postManual(task()))

        assertSilent(manager.activeNotifications.single().notification)
        val channel = manager.getNotificationChannel(TaskNotifier.ChannelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(sound, channel.sound)
        assertTrue(channel.shouldVibrate())
        assertTrue(vibration.contentEquals(channel.vibrationPattern))
    }

    @Test
    @Config(sdk = [30, 33, 36])
    fun repeatedTapRefreshesOneNotificationWithCurrentContentAndTarget() {
        val task = task()
        notifier.postManual(task)
        val updated = task.copy(title = "Updated title", filePath = "Tasks/Renamed.md")

        notifier.postManual(updated)

        val notification = manager.activeNotifications.single().notification
        assertSilent(notification)
        assertEquals(updated.title, notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        notification.contentIntent.send()
        assertTaskTarget(shadowOf(context).nextStartedActivity, updated)
    }

    @Test
    fun differentTasksKeepIndependentNotificationAndPendingIntentTargets() {
        val first = task()
        val second = task().copy(id = 8, fileUri = "content://tasks/8", filePath = "Tasks/Other.md")

        notifier.postManual(first)
        notifier.postManual(second)

        val notifications = manager.activeNotifications
        assertEquals(2, notifications.size)
        assertNotEquals(notifications[0].notification.contentIntent, notifications[1].notification.contentIntent)
        listOf(first, second).forEach { task ->
            notifications.single { it.tag == "manual_task:${task.fileUri}" }.notification.contentIntent.send()
            assertTaskTarget(shadowOf(context).nextStartedActivity, task)
        }
    }

    @Test
    @Config(sdk = [30, 33, 36])
    fun manualNotificationsCoexistWithBothAutomaticReminderTypes() {
        val task = task()

        notifier.postManual(task)
        notifier.postManual(task)
        TaskReminderType.entries.forEach { notifier.postScheduled(task, it) }

        val notifications = manager.activeNotifications
        assertEquals(3, notifications.size)
        TaskReminderType.entries.forEach { type ->
            val notification = notifications.single { it.tag == null && it.id == taskReminderStableId(task.id, type) }.notification
            val summary = if (type == TaskReminderType.SCHEDULED) "Scheduled: ${task.scheduled}" else "Due: ${task.due}"
            assertEquals(summary, notification.extras.getCharSequence(Notification.EXTRA_TEXT))
            assertEquals(TaskNotifier.ChannelId, notification.channelId)
            assertEquals(Notification.GROUP_ALERT_ALL, notification.groupAlertBehavior)
            assertNull(notification.group)
            notification.contentIntent.send()
            assertTaskTarget(shadowOf(context).nextStartedActivity, task)
        }
        val manual = notifications.single { it.tag != null }.notification
        assertSilent(manual)
        assertNotEquals(manual.contentIntent, notifications.first { it.tag == null }.notification.contentIntent)
    }

    @Test
    fun doesNotPostWithoutRuntimePermission() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(TaskNotificationResult.DISABLED, notifier.postManual(task()))
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    fun doesNotPostWhenAppNotificationsAreDisabled() {
        shadowOf(manager).setNotificationsEnabled(false)

        assertEquals(TaskNotificationResult.DISABLED, notifier.postManual(task()))
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    fun doesNotPostOrReenableBlockedChannel() {
        manager.createNotificationChannel(
            NotificationChannel(TaskNotifier.ChannelId, "Task notifications", NotificationManager.IMPORTANCE_NONE)
        )

        assertEquals(TaskNotificationResult.DISABLED, notifier.postManual(task()))
        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(TaskNotifier.ChannelId).importance)
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    @Config(sdk = [30])
    fun postsOnAndroid11WithoutRuntimeNotificationPermission() {
        assertEquals(TaskNotificationResult.POSTED, notifier.postManual(task().copy(scheduled = null, due = null)))
        assertEquals(1, manager.activeNotifications.size)
    }

    private fun assertSilent(notification: Notification) {
        assertEquals(TaskNotifier.ChannelId, notification.channelId)
        assertNull(notification.sound)
        assertNull(notification.vibrate)
        assertEquals(0, notification.defaults and (Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE))
        // AndroidX suppresses channel alerts by making this a child of its silent group.
        assertEquals(Notification.GROUP_ALERT_SUMMARY, notification.groupAlertBehavior)
        assertFalse(notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertEquals("silent", notification.group)
    }

    private fun assertTaskTarget(intent: Intent, task: StoredTask) {
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(task.vaultUri, intent.getStringExtra(ObsidianProjectOpener.ExtraVaultUri))
        assertEquals(task.filePath, intent.getStringExtra(ObsidianProjectOpener.ExtraFilePath))
        // Android parcels a separate delivery; Robolectric can return the PendingIntent's original object.
        val deliveredIntent = Intent(intent)
        assertEquals(
            ObsidianTaskNotesTarget(task.vaultUri, task.filePath),
            ObsidianProjectOpener.consumeTaskOpenTarget(deliveredIntent)
        )
        assertFalse(deliveredIntent.hasExtra(ObsidianProjectOpener.ExtraFilePath))
    }

    private fun task(): StoredTask = StoredTask(
        id = 7,
        vaultUri = "content://vaults/personal",
        fileUri = "content://tasks/7",
        filePath = "Tasks/Review café & plans.md",
        fileName = "Review café & plans.md",
        title = "Review plans",
        excerpt = "Bring the project notes.",
        status = "open",
        statusColor = null,
        priority = null,
        priorityColor = null,
        priorityWeight = null,
        scheduled = "2099-05-01T09:00:00",
        due = "2099-05-02",
        dateCreated = null,
        dateModified = null,
        tags = "project",
        lastModified = null
    )
}
