package com.sadparad1se.task_reminder

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Scans selected Obsidian vault folders and syncs TaskNotes Markdown files into Room. */
class VaultScanner(
    private val context: Context,
    private val taskDao: TaskDao,
    private val vaultScanStateDao: VaultScanStateDao,
    private val notificationScheduler: TaskNotificationScheduler,
    private val scanFailureNotifier: ScanFailureNotifier = ScanFailureNotifier(context),
    private val parser: MarkdownTaskParser = MarkdownTaskParser(),
    private val taskNotesSettingsParser: TaskNotesSettingsParser = TaskNotesSettingsParser(),
    private val now: () -> Long = System::currentTimeMillis
) {
    private val taskNotesSettingsCache = mutableMapOf<String, CachedTaskNotesSettings>()

    /** Scans one vault URI, updating scan state and notifying the user if the scan fails. */
    suspend fun scanVault(vaultUri: String): VaultScanResult = withContext(Dispatchers.IO) {
        val scanStartedAt = now()
        try {
            val previousScanAt = vaultScanStateDao.get(vaultUri)?.lastScannedAt ?: 0L
            val vaultDocument = DocumentFile.fromTreeUri(context, Uri.parse(vaultUri))
                ?: throw IllegalStateException("Vault folder cannot be opened")
            if (!vaultDocument.isObsidianVaultRoot()) {
                throw IllegalStateException("Selected folder is not an Obsidian vault")
            }
            val loadedSettings = loadTaskNotesSettings(vaultUri, vaultDocument)
            val tasksFolderDocument = vaultDocument.findDirectoryByRelativePath(loadedSettings.settings.tasksFolder)
                ?: throw IllegalStateException("TaskNotes tasksFolder cannot be opened")
            val lastScannedAt = if (loadedSettings.forceFullRescan) {
                taskDao.getTasksByVaultUri(vaultUri)
                    .forEach { task -> notificationScheduler.cancelTaskNotifications(task.id) }
                taskDao.deleteByVaultUri(vaultUri)
                0L
            } else {
                previousScanAt
            }

            val seenFileUris = mutableSetOf<String>()
            val taskActivityFound = scanDocumentTreeEntry(
                vaultUri = vaultUri,
                document = tasksFolderDocument,
                relativePath = loadedSettings.settings.tasksFolder,
                lastScannedAt = lastScannedAt,
                previousScanAt = previousScanAt,
                scanStartedAt = scanStartedAt,
                taskNotesSettings = loadedSettings.settings,
                seenFileUris = seenFileUris
            )
            pruneDeletedTasks(vaultUri, seenFileUris)

            vaultScanStateDao.upsert(
                VaultScanState(
                    vaultUri = vaultUri,
                    lastScannedAt = scanStartedAt,
                    lastScanError = null,
                    lastScanFailedAt = null
                )
            )
            VaultScanResult(true, scanStartedAt, previousScanAt <= 0L || taskActivityFound)
        } catch (exception: Exception) {
            // A partially rebuilt database must be rebuilt again on the next attempt.
            taskNotesSettingsCache.remove(vaultUri)
            if (exception is CancellationException) throw exception
            val previousState = vaultScanStateDao.get(vaultUri)
            vaultScanStateDao.upsert(
                VaultScanState(
                    vaultUri = vaultUri,
                    lastScannedAt = previousState?.lastScannedAt ?: 0L,
                    lastScanError = exception.shortMessage(),
                    lastScanFailedAt = scanStartedAt
                )
            )
            scanFailureNotifier.notifyScanFailed()
            VaultScanResult(false, scanStartedAt)
        }
    }

    /** Clears cached TaskNotes settings for a vault that is no longer tracked. */
    fun clearCachedTaskNotesSettings(vaultUri: String) {
        taskNotesSettingsCache.remove(vaultUri)
    }

    /** Loads TaskNotes settings, reusing the cached copy when the settings file timestamp is unchanged. */
    private fun loadTaskNotesSettings(vaultUri: String, vaultDocument: DocumentFile): LoadedTaskNotesSettings {
        val settingsFile = vaultDocument.findFileByRelativePath(TaskNotesSettingsPath)
            ?: throw IllegalStateException("TaskNotes data.json cannot be opened")
        val lastModified = queryLastModifiedMillis(settingsFile.uri)
        val cachedSettings = taskNotesSettingsCache[vaultUri]
        if (lastModified != null && cachedSettings?.lastModified == lastModified) {
            return LoadedTaskNotesSettings(
                settings = cachedSettings.settings,
                forceFullRescan = false
            )
        }

        val settings = taskNotesSettingsParser.parse(
            readDocumentText(settingsFile.uri) ?: throw IllegalStateException("TaskNotes data.json cannot be read")
        )
        if (lastModified != null) {
            taskNotesSettingsCache[vaultUri] = CachedTaskNotesSettings(settings, lastModified)
        } else {
            taskNotesSettingsCache.remove(vaultUri)
        }

        return LoadedTaskNotesSettings(
            settings = settings,
            forceFullRescan = true
        )
    }

    /** Returns a short error message suitable for scan status UI and notifications. */
    private fun Exception.shortMessage(): String {
        return localizedMessage
            ?.takeIf { it.isNotBlank() }
            ?.take(120)
            ?: this::class.java.simpleName
    }

    /** Reads task dates before filtering completed tasks or applying incremental database updates. */
    private suspend fun scanDocumentTreeEntry(
        vaultUri: String,
        document: DocumentFile,
        relativePath: String,
        lastScannedAt: Long,
        previousScanAt: Long,
        scanStartedAt: Long,
        taskNotesSettings: TaskNotesSettings,
        seenFileUris: MutableSet<String>
    ): Boolean {
        if (document.isDirectory) {
            var foundActivity = false
            document.listFiles().forEach { child ->
                val childName = child.name ?: error("Vault entry cannot be read")
                val childPath = if (relativePath.isBlank()) childName else "$relativePath/$childName"
                val childActivity = scanDocumentTreeEntry(
                    vaultUri = vaultUri,
                    document = child,
                    relativePath = childPath,
                    lastScannedAt = lastScannedAt,
                    previousScanAt = previousScanAt,
                    scanStartedAt = scanStartedAt,
                    taskNotesSettings = taskNotesSettings,
                    seenFileUris = seenFileUris
                )
                foundActivity = foundActivity || childActivity
            }
            return foundActivity
        }

        val fileName = document.name ?: error("Vault entry cannot be read")
        if (!fileName.endsWith(".md", ignoreCase = true)) return false
        seenFileUris.add(document.uri.toString())

        val lastModified = queryLastModifiedMillis(document.uri)
        // File timestamps alone cannot tell us whether TaskNotes dateModified changed.
        val markdown = readDocumentText(document.uri) ?: error("Task file cannot be read: $fileName")
        val parsedTaskNote = parser.parseTaskNote(
            markdown = markdown,
            fileName = fileName
        )
        val updated = parsedTaskNote?.wasUpdatedBetween(previousScanAt, scanStartedAt, lastModified) == true
        if (lastModified == null || lastModified > lastScannedAt || updated) syncParsedTaskWithDatabase(
            parsedTaskNote = parsedTaskNote,
            vaultUri = vaultUri,
            document = document,
            relativePath = relativePath,
            fileName = fileName,
            lastModified = lastModified,
            taskNotesSettings = taskNotesSettings
        )
        return updated
    }

    /** Upserts active task metadata or deletes rows for non-task notes and completed statuses. */
    private suspend fun syncParsedTaskWithDatabase(
        parsedTaskNote: ParsedTaskNote?,
        vaultUri: String,
        document: DocumentFile,
        relativePath: String,
        fileName: String,
        lastModified: Long?,
        taskNotesSettings: TaskNotesSettings
    ) {
        val fileUri = document.uri.toString()
        val storedTask = taskDao.getByFileUri(document.uri.toString())
        if (parsedTaskNote == null || taskNotesSettings.isCompletedStatus(parsedTaskNote.status)) {
            if (storedTask != null) notificationScheduler.cancelTaskNotifications(storedTask.id)
            taskDao.deleteByFileUri(fileUri)
            return
        }

        taskDao.upsert(
            StoredTask(
                id = storedTask?.id ?: 0,
                vaultUri = vaultUri,
                fileUri = fileUri,
                filePath = relativePath,
                fileName = fileName,
                title = parsedTaskNote.title,
                excerpt = parsedTaskNote.excerpt,
                status = parsedTaskNote.status,
                statusColor = taskNotesSettings.colorForStatus(parsedTaskNote.status),
                priority = parsedTaskNote.priority,
                priorityColor = taskNotesSettings.colorForPriority(parsedTaskNote.priority),
                priorityWeight = taskNotesSettings.weightForPriority(parsedTaskNote.priority),
                scheduled = parsedTaskNote.scheduled,
                due = parsedTaskNote.due,
                dateCreated = parsedTaskNote.dateCreated,
                dateModified = parsedTaskNote.dateModified,
                tags = parsedTaskNote.tags.joinToString("\n"),
                lastModified = lastModified
            )
        )
        taskDao.getByFileUri(fileUri)?.let { savedTask ->
            notificationScheduler.rescheduleTask(savedTask)
        }
    }

    /** Removes stored tasks whose source Markdown files are no longer present in the tasks folder. */
    private suspend fun pruneDeletedTasks(vaultUri: String, seenFileUris: Set<String>) {
        taskDao.getTasksByVaultUri(vaultUri)
            .filter { task -> task.fileUri !in seenFileUris }
            .forEach { task ->
                notificationScheduler.cancelTaskNotifications(task.id)
                taskDao.deleteByFileUri(task.fileUri)
            }
    }

    /** Reads a document URI as UTF-8 text through Android's content resolver. */
    private fun readDocumentText(uri: Uri): String? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        }
    }

    /** Returns the provider-reported last-modified time, or null when unavailable or invalid. */
    private fun queryLastModifiedMillis(uri: Uri): Long? {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val queriedValue = context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val columnIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            if (columnIndex < 0 || cursor.isNull(columnIndex)) null else cursor.getLong(columnIndex)
        }

        return queriedValue?.takeIf { it > 0 }
    }

    /** Finds a direct or nested child file by slash-separated path relative to this directory. */
    private fun DocumentFile.findFileByRelativePath(relativePath: String): DocumentFile? {
        return relativePath.split('/')
            .filter { it.isNotBlank() }
            .fold(this as DocumentFile?) { parent, segment -> parent?.findFile(segment) }
    }

    /** Finds a nested directory by relative path and returns null if the path does not resolve to a directory. */
    private fun DocumentFile.findDirectoryByRelativePath(relativePath: String): DocumentFile? {
        return findFileByRelativePath(relativePath)?.takeIf { it.isDirectory }
    }

    private companion object {
        const val TaskNotesSettingsPath = ".obsidian/plugins/tasknotes/data.json"
    }

    /** Parsed TaskNotes settings cached with the provider-reported settings file timestamp. */
    private data class CachedTaskNotesSettings(
        val settings: TaskNotesSettings,
        val lastModified: Long
    )

    /** TaskNotes settings returned for a scan and whether tasks must be fully rebuilt. */
    private data class LoadedTaskNotesSettings(
        val settings: TaskNotesSettings,
        val forceFullRescan: Boolean
    )
}
