package com.sadparad1se.task_reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class ScanStatusFormatterTest {
    /** Verifies absent and empty scan states are displayed as not scanned. */
    @Test
    fun formatsMissingScanState() {
        assertEquals("Not scanned yet", formatScanStatus(null))
        assertEquals(
            "Not scanned yet",
            formatScanStatus(VaultScanState(vaultUri = "vault", lastScannedAt = 0L))
        )
    }

    /** Verifies scan errors take precedence over the last successful scan timestamp. */
    @Test
    fun formatsScanErrorBeforeTimestamp() {
        assertEquals(
            "Last scan failed: TaskNotes data.json cannot be read",
            formatScanStatus(
                VaultScanState(
                    vaultUri = "vault",
                    lastScannedAt = 1_704_067_200_000L,
                    lastScanError = "TaskNotes data.json cannot be read",
                    lastScanFailedAt = 1_704_070_800_000L
                )
            )
        )
    }

    /** Verifies successful scan timestamps format using the device timezone. */
    @Test
    fun formatsSuccessfulTimestamp() {
        val previousTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

            assertEquals(
                "Last scanned: 2024-01-01 00:00",
                formatScanStatus(VaultScanState(vaultUri = "vault", lastScannedAt = 1_704_067_200_000L))
            )
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }
}
