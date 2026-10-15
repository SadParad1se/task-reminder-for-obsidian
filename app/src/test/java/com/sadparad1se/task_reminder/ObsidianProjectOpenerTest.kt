package com.sadparad1se.task_reminder

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ObsidianProjectOpenerTest {
    private val vaultUri = "content://com.android.externalstorage.documents/tree/primary%3AMy%20Vault"
    private val filePath = "Tasks/Review café & plans #1.md"

    @Test
    fun consumesTaskRequestOnce() {
        val intent = taskIntent().putExtra("unrelated", "preserved")

        assertEquals(
            ObsidianTaskNotesTarget(vaultUri, filePath),
            ObsidianProjectOpener.consumeTaskOpenTarget(intent)
        )
        assertTargetRemoved(intent)
        assertEquals("preserved", intent.getStringExtra("unrelated"))
        assertNull(ObsidianProjectOpener.consumeTaskOpenTarget(intent))
    }

    @Test
    fun ignoresNormalLauncherIntent() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        assertNull(ObsidianProjectOpener.consumeTaskOpenTarget(intent))
        assertEquals(Intent.ACTION_MAIN, intent.action)
    }

    @Test
    fun discardsIncompleteAndBlankRequests() {
        val invalidValues = listOf(
            null to null,
            vaultUri to null,
            null to filePath,
            "" to filePath,
            " \t" to filePath,
            vaultUri to "",
            vaultUri to " \n"
        )
        invalidValues.forEach { (vault, path) ->
            val intent = Intent().apply {
                vault?.let { putExtra(ObsidianProjectOpener.ExtraVaultUri, it) }
                path?.let { putExtra(ObsidianProjectOpener.ExtraFilePath, it) }
            }

            assertNull(ObsidianProjectOpener.consumeTaskOpenTarget(intent))
            assertTargetRemoved(intent)
        }
    }

    @Test
    fun discardsRequestOnSavedStateRestoration() {
        val intent = taskIntent()

        assertNull(ObsidianProjectOpener.consumeTaskOpenTarget(intent, isRestored = true))
        assertTargetRemoved(intent)
        assertNull(ObsidianProjectOpener.consumeTaskOpenTarget(intent))
    }

    @Test
    fun discardsRequestRelaunchedFromHistory() {
        val intent = taskIntent().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertNull(ObsidianProjectOpener.consumeTaskOpenTarget(intent))
        assertTargetRemoved(intent)
    }

    @Test
    fun acceptsFreshTapOnSameTask() {
        val expected = ObsidianTaskNotesTarget(vaultUri, filePath)

        assertEquals(expected, ObsidianProjectOpener.consumeTaskOpenTarget(taskIntent()))
        assertEquals(expected, ObsidianProjectOpener.consumeTaskOpenTarget(taskIntent()))
    }

    @Test
    fun acceptsFreshRequestAfterRestorationAndHistoryLaunch() {
        ObsidianProjectOpener.consumeTaskOpenTarget(taskIntent(), isRestored = true)
        ObsidianProjectOpener.consumeTaskOpenTarget(
            taskIntent().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        )
        val nextIntent = taskIntent().putExtra(ObsidianProjectOpener.ExtraFilePath, "Tasks/Another task.md")

        assertEquals(
            ObsidianTaskNotesTarget(vaultUri, "Tasks/Another task.md"),
            ObsidianProjectOpener.consumeTaskOpenTarget(nextIntent)
        )
        assertTargetRemoved(nextIntent)
    }

    @Test
    fun createsReusableNotificationIntentWithExactTarget() {
        val context = RuntimeEnvironment.getApplication()
        val task = StoredTask(
            id = 42,
            vaultUri = vaultUri,
            fileUri = "$vaultUri/document/task",
            filePath = filePath,
            fileName = "Review café & plans #1.md",
            title = "Review plans",
            excerpt = "",
            status = null,
            statusColor = null,
            priority = null,
            priorityColor = null,
            priorityWeight = null,
            scheduled = null,
            due = null,
            dateCreated = null,
            dateModified = null,
            tags = "",
            lastModified = null
        )

        val intent = ObsidianProjectOpener.createTaskOpenIntent(context, task)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            intent.flags
        )
        assertEquals(vaultUri, intent.getStringExtra(ObsidianProjectOpener.ExtraVaultUri))
        assertEquals(filePath, intent.getStringExtra(ObsidianProjectOpener.ExtraFilePath))
    }

    private fun taskIntent(): Intent = Intent()
        .putExtra(ObsidianProjectOpener.ExtraVaultUri, vaultUri)
        .putExtra(ObsidianProjectOpener.ExtraFilePath, filePath)

    private fun assertTargetRemoved(intent: Intent) {
        assertFalse(intent.hasExtra(ObsidianProjectOpener.ExtraVaultUri))
        assertFalse(intent.hasExtra(ObsidianProjectOpener.ExtraFilePath))
    }
}
