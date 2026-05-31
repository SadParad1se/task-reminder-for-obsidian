package com.sadparad1se.task_reminder

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Room DAO for per-vault scan status rows. */
@Dao
interface VaultScanStateDao {
    /** Observes scan status for every selected vault. */
    @Query("SELECT * FROM vault_scan_states ORDER BY vaultUri ASC")
    fun observeAll(): Flow<List<VaultScanState>>

    /** Returns scan status for one vault, or null before its first scan. */
    @Query("SELECT * FROM vault_scan_states WHERE vaultUri = :vaultUri")
    suspend fun get(vaultUri: String): VaultScanState?

    /** Inserts or replaces scan status for one vault. */
    @Upsert
    suspend fun upsert(scanState: VaultScanState)

    /** Removes scan status when a vault is no longer tracked. */
    @Query("DELETE FROM vault_scan_states WHERE vaultUri = :vaultUri")
    suspend fun deleteByVaultUri(vaultUri: String)
}
