package com.sadparad1se.task_reminder

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile

/** Minimal information needed to open a task file in Obsidian. */
data class ObsidianTaskNotesTarget(
    val vaultUri: String,
    val filePath: String
)

/** Opens Obsidian task files from task rows and notifications. */
object ObsidianProjectOpener {
    const val ExtraVaultUri = "open_vault_uri"
    const val ExtraFilePath = "open_file_path"

    /** Creates the app intent used when the user taps a task reminder notification. */
    fun createTaskOpenIntent(context: Context, task: StoredTask): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ExtraVaultUri, task.vaultUri)
            putExtra(ExtraFilePath, task.filePath)
        }
    }

    /** Extracts a TaskNotes open target from an incoming app intent. */
    fun targetFromIntent(intent: Intent): ObsidianTaskNotesTarget? {
        val vaultUri = intent.getStringExtra(ExtraVaultUri)?.takeIf { it.isNotBlank() } ?: return null
        val filePath = intent.getStringExtra(ExtraFilePath)?.takeIf { it.isNotBlank() } ?: return null
        return ObsidianTaskNotesTarget(vaultUri, filePath)
    }

    /** Opens the Obsidian file for a visible task list item. */
    suspend fun openTaskNotes(context: Context, task: TaskListItem, showToast: Boolean = true) {
        openTaskNotes(
            context = context,
            target = ObsidianTaskNotesTarget(vaultUri = task.vaultUri, filePath = task.filePath),
            showToast = showToast
        )
    }

    /** Opens the target task file in Obsidian, falling back to Obsidian when needed. */
    suspend fun openTaskNotes(context: Context, target: ObsidianTaskNotesTarget, showToast: Boolean = true) {
        if (tryOpenTaskFile(context, target.vaultUri, target.filePath)) return
        if (tryOpenObsidian(context)) {
            if (showToast) {
                Toast.makeText(
                    context,
                    "Task file could not be opened. Make sure Obsidian Advanced URI is installed and enabled.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        if (showToast) {
            Toast.makeText(
                context,
                "Obsidian is not installed or cannot be opened.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Tries to launch Obsidian Advanced URI directly into a task file. */
    private fun tryOpenTaskFile(context: Context, vaultUri: String, filePath: String): Boolean {
        val vaultName = getVaultName(context, vaultUri) ?: return false
        return tryStartActivity(
            context = context,
            intent = Intent(
                Intent.ACTION_VIEW,
                Uri.Builder()
                    .scheme("obsidian")
                    .authority(AdvancedUriAuthority)
                    .appendQueryParameter("vault", vaultName)
                    .appendQueryParameter("filepath", filePath)
                    .build()
            )
        )
    }

    /** Resolves the Obsidian vault name from a persisted tree URI. */
    private fun getVaultName(context: Context, vaultUri: String): String? {
        return DocumentFile.fromTreeUri(context, Uri.parse(vaultUri))?.name
            ?.takeIf { it.isNotBlank() }
    }

    /** Tries to open the Obsidian app itself as a fallback. */
    private fun tryOpenObsidian(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(ObsidianPackageName) ?: return false
        return tryStartActivity(context, intent)
    }

    /** Starts an activity and returns false when Android rejects the intent. */
    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        val launchIntent = intent.apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(launchIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private const val AdvancedUriAuthority = "adv-uri"
}
