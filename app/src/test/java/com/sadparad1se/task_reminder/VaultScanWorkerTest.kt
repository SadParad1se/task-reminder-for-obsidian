package com.sadparad1se.task_reminder

import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultScanWorkerTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun noSelectedVaultsCompletesSuccessfully() = runBlocking {
        val worker = TestListenableWorkerBuilder<VaultScanWorker>(context).build()
        assertEquals(Result.success(), worker.doWork())
    }

    @Test
    fun foregroundAndBackgroundScansUseTheSavedCustomConfiguration() = runBlocking {
        try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        val root = File(context.cacheDir, "worker-vault").apply { mkdirs() }
        File(root, "Phone settings/plugins/tasknotes/data.json").apply {
            parentFile!!.mkdirs()
            writeText("""{"tasksFolder":"Tasks"}""")
        }
        val task = File(root, "Tasks/task.md").apply {
            parentFile!!.mkdirs()
            writeText("---\ntags: [task]\nstatus: open\n---\n# Phone task")
        }
        TestVaultDocumentsProvider(root).also { provider ->
            provider.attachInfo(context, ProviderInfo().apply {
                authority = "vault.worker"
                exported = true
                grantUriPermissions = true
            })
            ShadowContentResolver.registerProviderInternal("vault.worker", provider)
        }
        val uri = DocumentsContract.buildTreeDocumentUri("vault.worker", "root").toString()
        SettingsRepository(context).addVaultUri(uri, "Phone settings")
        assertTrue(TaskRepository(context).scanVault(uri))
        assertEquals(1, TaskDatabase.getInstance(context).taskDao().getAllTasks().size)
        task.delete()
        val worker = TestListenableWorkerBuilder<VaultScanWorker>(context).build()
        assertEquals(Result.success(), worker.doWork())
        assertTrue(TaskDatabase.getInstance(context).taskDao().getAllTasks().isEmpty())
    }

    @Test
    fun cancellationWhileLoadingSettingsDoesNotBecomeARetry() = runBlocking {
        val worker = TestListenableWorkerBuilder<VaultScanWorker>(context).build()
        var result: Result? = null
        launch(start = CoroutineStart.UNDISPATCHED) {
            coroutineContext.cancel()
            result = worker.doWork()
        }.join()
        assertNull("A canceled worker must propagate cancellation instead of returning retry", result)
    }
}
