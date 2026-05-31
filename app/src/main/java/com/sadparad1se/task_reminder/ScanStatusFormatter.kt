package com.sadparad1se.task_reminder

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Formats vault scan state for display in settings. */
fun formatScanStatus(scanState: VaultScanState?): String {
    if (scanState == null) return "Not scanned yet"
    scanState.lastScanError?.takeIf { it.isNotBlank() }?.let { error ->
        return "Last scan failed: $error"
    }
    if (scanState.lastScannedAt <= 0L) return "Not scanned yet"
    return "Last scanned: ${formatEpochMillis(scanState.lastScannedAt)}"
}

/** Formats epoch milliseconds using the device timezone. */
private fun formatEpochMillis(epochMillis: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
}
