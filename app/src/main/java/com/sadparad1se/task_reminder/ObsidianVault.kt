package com.sadparad1se.task_reminder

import androidx.documentfile.provider.DocumentFile

/** Returns true when this vault contains the chosen configuration directory as a direct child. */
fun DocumentFile.isObsidianVaultRoot(
    configurationFolder: String = DefaultObsidianConfigurationFolder
): Boolean {
    return isDirectory && listFiles().any { child ->
        child.isDirectory && child.name == configurationFolder
    }
}
