package com.sadparad1se.task_reminder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Screen-owned loading state that prevents overlapping foreground scans. */
internal class ForegroundScanState {
    var isScanning by mutableStateOf(false)
        private set

    suspend fun run(action: suspend () -> Unit) {
        if (isScanning) return
        isScanning = true
        try {
            action()
        } finally {
            isScanning = false
        }
    }
}
