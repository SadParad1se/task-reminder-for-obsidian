package com.sadparad1se.task_reminder

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ConfigurationFolderChoice(
    val vaultUri: String,
    val folders: List<String>,
    val currentFolder: String?
)

/** Chooses a direct child directory using the vault's existing read access. */
@Composable
internal fun rememberConfigurationFolderPicker(
    onFolderSelected: suspend (String, String) -> Unit
): (String, String?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf<ConfigurationFolderChoice?>(null) }

    choice?.let { pending ->
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text("Configuration folder") },
            text = {
                Column {
                    Text("Choose the configuration folder Obsidian uses for this vault on this device.")
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(pending.folders) { folder ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    choice = null
                                    scope.launch { onFolderSelected(pending.vaultUri, folder) }
                                }
                            ) {
                                Text(if (folder == pending.currentFolder) "$folder (current)" else folder)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choice = null }) { Text("Cancel") } }
        )
    }

    return { vaultUri, currentFolder ->
        scope.launch {
            val folders = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(context, Uri.parse(vaultUri))?.listFiles().orEmpty()
                    .filter { it.isDirectory }
                    .mapNotNull { it.name }
                    .sorted()
            }
            if (folders.isEmpty()) {
                Toast.makeText(context, InvalidObsidianVaultMessage, Toast.LENGTH_LONG).show()
            } else {
                choice = ConfigurationFolderChoice(vaultUri, folders, currentFolder)
            }
        }
    }
}
