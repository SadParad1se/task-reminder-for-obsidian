package com.sadparad1se.task_reminder

import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultScannerActivityTest {
    private val context = RuntimeEnvironment.getApplication()
    private val database = Room.inMemoryDatabaseBuilder(context, TaskDatabase::class.java).build()
    private val root = File(context.cacheDir, "test-vault").apply { mkdirs() }
    private val provider = TestVaultDocumentsProvider(root)
    private val uri = DocumentsContract.buildTreeDocumentUri("vault.test", "root").toString()
    private var clock = System.currentTimeMillis() + 10_000
    private val hour = 3_600_000L
    private lateinit var scanner: VaultScanner

    @Before
    fun prepareVault() {
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "vault.test"
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        })
        ShadowContentResolver.registerProviderInternal("vault.test", provider)
        write(".obsidian/plugins/tasknotes/data.json", """{"tasksFolder":"Tasks","customStatuses":[{"value":"done","isCompleted":true}]}""")
        File(root, "Tasks").mkdirs()
        scanner = newScanner()
    }

    @After
    fun closeDatabase() { database.close() }

    private fun newScanner() = VaultScanner(
        context, database.taskDao(), database.vaultScanStateDao(),
        TaskNotificationScheduler(context, database.taskDao()), now = { clock }
    )

    private fun write(path: String, text: String, modifiedAt: Long = clock - 2 * hour): File {
        return File(root, path).apply {
            parentFile?.mkdirs()
            writeText(text)
            setLastModified(modifiedAt)
        }
    }

    private fun taskText(dateModified: Long? = clock - 2 * hour, completed: Boolean = false): String {
        val modified = dateModified?.let { "dateModified: ${Instant.ofEpochMilli(it)}" }.orEmpty()
        return "---\ntags: [task]\nstatus: ${if (completed) "done" else "open"}\n$modified\n---\n# Example task\n"
    }

    @Test
    fun customConfigurationWorksWithoutDefaultDirectory() = runBlocking {
        File(root, ".obsidian").renameTo(File(root, ".obsidianHandy1"))
        write("Tasks/task.md", taskText())
        assertTrue(scanner.scanVault(uri, ".obsidianHandy1").succeeded)
        assertEquals(1, database.taskDao().getAllTasks().size)
    }

    @Test
    fun switchingFoldersWithIdenticalSettingsTimestampsRebuildsTasksAndSupportsOrdinaryNames() = runBlocking {
        write("Tasks/desktop.md", taskText())
        write("Phone settings/plugins/tasknotes/data.json", """{"tasksFolder":"Phone tasks"}""")
        write("Phone tasks/mobile.md", taskText())
        assertEquals(File(root, ".obsidian/plugins/tasknotes/data.json").lastModified(),
            File(root, "Phone settings/plugins/tasknotes/data.json").lastModified())
        assertTrue(scanner.scanVault(uri).succeeded)
        assertEquals("desktop.md", database.taskDao().getAllTasks().single().fileName)
        clock += hour
        assertTrue(scanner.scanVault(uri, "Phone settings").succeeded)
        assertEquals("mobile.md", database.taskDao().getAllTasks().single().fileName)
        clock += hour
        assertTrue(scanner.scanVault(uri).succeeded)
        assertEquals("desktop.md", database.taskDao().getAllTasks().single().fileName)
    }

    @Test
    fun missingOrUnreadableChosenConfigurationFailsWithoutFallingBack() = runBlocking {
        write("Tasks/task.md", taskText())
        assertTrue(scanner.scanVault(uri).succeeded)
        val checkpoint = database.vaultScanStateDao().get(uri)!!.lastScannedAt
        clock += hour
        assertFalse(scanner.scanVault(uri, ".missing").succeeded)
        assertEquals(checkpoint, database.vaultScanStateDao().get(uri)!!.lastScannedAt)
        write("custom/plugins/tasknotes/data.json", """{"tasksFolder":"Tasks"}""")
        provider.unreadable = "root/custom/plugins/tasknotes/data.json"
        assertFalse(scanner.scanVault(uri, "custom").succeeded)
        assertEquals(checkpoint, database.vaultScanStateDao().get(uri)!!.lastScannedAt)
        assertEquals(1, database.taskDao().getAllTasks().size)
    }

    @Test
    fun firstSuccessfulScanCountsEvenWhenEmptyOrAllTasksAreOld() = runBlocking {
        val initial = scanner.scanVault(uri)
        assertTrue(database.vaultScanStateDao().get(uri)?.lastScanError, initial.succeeded)
        assertTrue(initial.taskActivityFound)
        clock += hour
        write("Tasks/old.md", taskText(clock - 3 * hour))
        val result = scanner.scanVault(uri)
        assertTrue(result.succeeded)
        assertFalse(result.taskActivityFound)
    }

    @Test
    fun forcedRescanAndScannerRestartDoNotCountUnchangedTasksAsActivity() = runBlocking {
        write("Tasks/task.md", taskText())
        assertTrue(scanner.scanVault(uri).taskActivityFound)
        clock += hour
        assertFalse(scanner.scanVault(uri).taskActivityFound)
        clock += hour
        // A new scanner has no settings cache and forces database rebuilding.
        assertFalse(newScanner().scanVault(uri).taskActivityFound)
        assertEquals(1, database.taskDao().getAllTasks().size)
    }

    @Test
    fun completionCountsBeforeCompletedTaskIsRemovedFromActiveDatabase() = runBlocking {
        write("Tasks/task.md", taskText())
        scanner.scanVault(uri)
        clock += hour
        write("Tasks/task.md", taskText(clock - 1, completed = true), modifiedAt = clock - 1)
        val result = scanner.scanVault(uri)
        assertTrue(result.taskActivityFound)
        assertTrue(database.taskDao().getAllTasks().isEmpty())
    }

    @Test
    fun updatedTaskNotesDateCountsEvenWhenFileTimestampIsUnchanged() = runBlocking {
        val oldFileTime = clock - 2 * hour
        write("Tasks/task.md", taskText(), oldFileTime)
        scanner.scanVault(uri)
        clock += hour
        write("Tasks/task.md", taskText(clock - 1), oldFileTime)
        assertTrue(scanner.scanVault(uri).taskActivityFound)
        assertEquals(Instant.ofEpochMilli(clock - 1).toString(), database.taskDao().getAllTasks().single().dateModified)
    }

    @Test
    fun bodyEditCountsFromFileDateButOrdinaryNotesDoNot() = runBlocking {
        write("Tasks/task.md", taskText())
        scanner.scanVault(uri)
        clock += hour
        write("Tasks/ordinary.md", "# Not a task", clock - 1)
        assertFalse(scanner.scanVault(uri).taskActivityFound)
        clock += hour
        write("Tasks/task.md", taskText(clock - 4 * hour) + "Edited body", clock - 1)
        assertTrue(scanner.scanVault(uri).taskActivityFound)
    }

    @Test
    fun editsAfterScanStartAreDetectedByNextScanAndFutureDatesDoNotResetForever() = runBlocking {
        write("Tasks/task.md", taskText())
        scanner.scanVault(uri)
        clock += hour
        val editTime = clock + 1
        write("Tasks/task.md", taskText(editTime), editTime)
        assertFalse(scanner.scanVault(uri).taskActivityFound)
        clock += hour
        assertTrue(scanner.scanVault(uri).taskActivityFound)
        clock += hour
        assertFalse(scanner.scanVault(uri).taskActivityFound)
    }

    @Test
    fun unreadableTaskFailsScanWithoutAdvancingCheckpointOrClaimingActivity() = runBlocking {
        write("Tasks/task.md", taskText())
        scanner.scanVault(uri)
        val previous = database.vaultScanStateDao().get(uri)!!.lastScannedAt
        clock += hour
        write("Tasks/task.md", taskText(clock - 1), clock - 1)
        provider.unreadable = "root/Tasks/task.md"
        val result = scanner.scanVault(uri)
        assertFalse(result.succeeded)
        assertFalse(result.taskActivityFound)
        assertEquals(previous, database.vaultScanStateDao().get(uri)!!.lastScannedAt)
        provider.unreadable = null
        assertTrue(scanner.scanVault(uri).taskActivityFound)
    }

    @Test
    fun missingDatesNeverTurnAnUnchangedFileIntoRepeatedActivity() {
        val parsed = MarkdownTaskParser().parseTaskNote(taskText(null), "task.md")!!
        assertFalse(parsed.wasUpdatedBetween(clock - hour, clock, null))
        assertFalse(parsed.copy(dateModified = "invalid").wasUpdatedBetween(clock - hour, clock, null))
        assertTrue(parsed.wasUpdatedBetween(clock - hour, clock, clock))
    }
}
