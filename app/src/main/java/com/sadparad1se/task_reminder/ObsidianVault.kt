package com.sadparad1se.task_reminder

import androidx.documentfile.provider.DocumentFile

/** Returns true when this document is an Obsidian vault root containing a direct `.obsidian` directory. */
fun DocumentFile.isObsidianVaultRoot(): Boolean {
    return isDirectory && listFiles().any { child ->
        child.isDirectory && child.name == ".obsidian"
    }
}
