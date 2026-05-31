package com.sadparad1se.task_reminder

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room entity that records the last successful or failed scan for one vault. */
@Entity(tableName = "vault_scan_states")
data class VaultScanState(
    @PrimaryKey val vaultUri: String,
    val lastScannedAt: Long,
    val lastScanError: String? = null,
    val lastScanFailedAt: Long? = null
)
