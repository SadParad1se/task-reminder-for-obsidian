package com.sadparad1se.task_reminder

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** Selects a new vault and grants read access before invoking the screen's scan action. */
@Composable
internal fun rememberVaultPicker(
    selectedVaultUris: List<String>,
    onVaultSelected: suspend (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null || uri.toString() in selectedVaultUris) {
            return@rememberLauncherForActivityResult
        }
        if (!isObsidianVault(context, uri)) {
            Toast.makeText(context, InvalidObsidianVaultMessage, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        persistVaultReadPermission(context, uri)
        scope.launch { onVaultSelected(uri.toString()) }
    }
    return { launcher.launch(createOpenVaultIntent()) }
}
