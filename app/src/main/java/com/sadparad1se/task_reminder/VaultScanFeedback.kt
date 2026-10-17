package com.sadparad1se.task_reminder

import android.content.Context
import android.widget.Toast

/** Foreground scan feedback; background workers retain their existing notifications. */
internal fun Context.showVaultScanFeedback(succeeded: Boolean) {
    if (!succeeded) {
        Toast.makeText(this, R.string.vault_scan_failed, Toast.LENGTH_LONG).show()
    }
}
