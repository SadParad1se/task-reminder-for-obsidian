package com.sadparad1se.task_reminder

import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.work.Configuration
import androidx.work.ListenableWorker.Result
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h1000dp")
class ConfigurationScanFeedbackTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private val context = RuntimeEnvironment.getApplication()
    private val vaultUri = DocumentsContract.buildTreeDocumentUri("vault.feedback", "root").toString()
    private val settings = SettingsRepository(context)

    @Before
    fun prepareVault() = runBlocking {
        try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        val root = File(context.cacheDir, "feedback-vault").apply { mkdirs() }
        File(root, ".obsidian").mkdirs()
        File(root, ".obsidianHandy1/plugins/tasknotes/data.json").apply {
            parentFile!!.mkdirs()
            writeText("""{"tasksFolder":"Tasks"}""")
        }
        File(root, "Tasks/task.md").apply {
            parentFile!!.mkdirs()
            writeText("---\ntags: [task]\nstatus: open\n---\n# Example")
        }
        TestVaultDocumentsProvider(root).also { provider ->
            provider.attachInfo(context, ProviderInfo().apply {
                authority = "vault.feedback"
                exported = true
                grantUriPermissions = true
            })
            ShadowContentResolver.registerProviderInternal("vault.feedback", provider)
        }
        settings.addVaultUri(vaultUri)
        ShadowToast.reset()
    }

    @After
    fun removeFixtureVault() = runBlocking {
        settings.removeVaultUri(vaultUri)
        TaskRepository(context).deleteVaultAppData(vaultUri)
    }

    @Test
    fun onboardingCanCancelThenRecoverFromFailedConfigurationWithoutAdvancing() = withScreen({ TaskReminderApp() }) {
        waitForText("Task Reminder for Obsidian")
        repeat(2) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithText("Configuration folder: .obsidian").performClick()
        waitForText("Cancel")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(".obsidian", runBlocking { settings.settingsFlow.first().vaultConfigurationFolders.getValue(vaultUri) })
        assertEquals(0, ShadowToast.shownToastCount())

        compose.onNodeWithText("Configuration folder: .obsidian").performClick()
        waitForText(".obsidian (current)")
        whileScanBlocked(listOf("Choose Obsidian vault", "Configuration folder: .obsidian")) {
            compose.onNodeWithText(".obsidian (current)").performClick()
        }
        waitForFailureToast()
        waitForScanFinished()
        compose.onNodeWithText("Choose Obsidian vault").assertIsDisplayed()
        compose.onNodeWithText("Configuration folder: .obsidian").assertIsDisplayed()
        assertNotNull(scanState()?.lastScanError)

        chooseWorkingConfiguration()
        compose.onNodeWithText("Choose Obsidian vault").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Allow notifications").assertIsDisplayed()
    }

    @Test
    fun settingsManualScanShowsFailureAndFolderChangeRescansSuccessfully() = withScreen({
        TaskReminderTheme { SettingsScreen() }
    }) {
        waitForText("Configuration folder: .obsidian")
        whileScanBlocked(listOf("Scan now", "Add Vault", "Remove", "Configuration folder: .obsidian")) {
            compose.onNodeWithText("Scan now").performClick()
        }
        waitForFailureToast()
        waitForScanFinished()
        chooseWorkingConfiguration()
    }

    @Test
    fun backgroundFailureKeepsItsRetryBehaviorWithoutShowingAToast() = runBlocking {
        val worker = TestListenableWorkerBuilder<VaultScanWorker>(context).build()
        assertEquals(Result.retry(), worker.doWork())
        assertEquals(0, ShadowToast.shownToastCount())
    }

    private fun chooseWorkingConfiguration() {
        compose.runOnIdle { ShadowToast.reset() }
        compose.onNodeWithText("Configuration folder: .obsidian").performClick()
        waitForText(".obsidianHandy1")
        whileScanBlocked(listOf("Configuration folder: .obsidian")) {
            compose.onNodeWithText(".obsidianHandy1").performClick()
        }
        waitForText("Configuration folder: .obsidianHandy1")
        compose.waitUntil(timeoutMillis = 10_000) {
            scanState()?.let { it.lastScannedAt > 0 && it.lastScanError == null } == true
        }
        waitForScanFinished()
        compose.onNodeWithText("Configuration folder: .obsidianHandy1").assertIsEnabled()
        assertEquals(".obsidianHandy1", runBlocking {
            settings.settingsFlow.first().vaultConfigurationFolders.getValue(vaultUri)
        })
        assertEquals(1, runBlocking { TaskDatabase.getInstance(context).taskDao().getAllTasks().size })
        assertEquals(0, ShadowToast.shownToastCount())
    }

    private fun scanState() = runBlocking { TaskDatabase.getInstance(context).vaultScanStateDao().get(vaultUri) }

    private val progress = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    private fun whileScanBlocked(disabledControls: List<String>, startScan: () -> Unit) {
        assertTrue(TaskActivityMonitor.mutex.tryLock())
        try {
            startScan()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(progress, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(progress, useUnmergedTree = true).assertIsDisplayed()
            disabledControls.forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
            assertEquals(0, ShadowToast.shownToastCount())
        } finally {
            TaskActivityMonitor.mutex.unlock()
        }
    }

    private fun waitForScanFinished() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(progress, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForFailureToast() {
        compose.waitUntil(timeoutMillis = 10_000) { ShadowToast.shownToastCount() > 0 }
        assertEquals(context.getString(R.string.vault_scan_failed), ShadowToast.getTextOfLatestToast())
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun withScreen(content: @Composable () -> Unit, check: () -> Unit) {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            controller.get().setContent(content = content)
            check()
        }
    }
}
