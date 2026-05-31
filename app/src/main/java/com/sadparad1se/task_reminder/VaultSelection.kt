package com.sadparad1se.task_reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** Vault selected in settings, including both its persisted URI and human-readable name. */
data class VaultSelection(
    val uri: Uri,
    val displayName: String
)

const val InvalidObsidianVaultMessage = "Selected folder is not an Obsidian vault. It must contain .obsidian."

/** Creates the Android document-tree picker intent used to select an Obsidian vault. */
fun createOpenVaultIntent(): Intent {
    return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    }
}

/** Resolves a display name for a selected vault URI. */
fun getVaultDisplayName(context: Context, uri: Uri): String {
    return DocumentFile.fromTreeUri(context, uri)?.name ?: uri.toString()
}

/** Returns true when the selected directory is an Obsidian vault root. */
fun isObsidianVault(context: Context, uri: Uri): Boolean {
    return DocumentFile.fromTreeUri(context, uri)?.isObsidianVaultRoot() == true
}

/** Persists read access to a selected vault tree URI. */
fun persistVaultReadPermission(context: Context, uri: Uri) {
    context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION
    )
}
