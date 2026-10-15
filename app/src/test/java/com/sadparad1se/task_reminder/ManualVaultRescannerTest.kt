package com.sadparad1se.task_reminder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ManualVaultRescannerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val job = SupervisorJob()
    private val store = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(job + Dispatchers.IO),
        produceFile = { File(context.cacheDir, "manual-${UUID.randomUUID()}.preferences_pb") }
    )
    private val settings = SettingsRepository(context, store)
    private var clock = 1_800_000_000_000L
    private val scheduled = mutableListOf<Pair<AppSettings, Long>>()
    private val scans = mutableListOf<List<String>>()

    @Before
    fun selectVaults() = runBlocking { setVaults("a", "b") }

    @After
    fun close() = runBlocking { job.cancelAndJoin() }

    private suspend fun setVaults(vararg vaults: String) {
        store.edit { it[stringSetPreferencesKey("vault_uris")] = vaults.toSet() }
    }

    private fun rescanner(scan: suspend (List<String>) -> Boolean) = ManualVaultRescanner(
        settings,
        { scans.add(it); scan(it) },
        { latest, completedAt -> scheduled.add(latest to completedAt) },
        { clock }
    )

    @Test
    fun successfulScanUsesCompletionTimeAndLatestFrequency() = runBlocking {
        val start = clock
        assertTrue(rescanner {
            clock += 30_000
            store.edit { it[stringPreferencesKey("scan_frequency")] = ScanFrequency.EVERY_6_HOURS.storageValue }
            true
        }.rescan())
        assertEquals(listOf(setOf("a", "b")), scans.map { it.toSet() })
        assertEquals(start + 30_000, scheduled.single().second)
        assertEquals(ScanFrequency.EVERY_6_HOURS, scheduled.single().first.scanFrequency)
    }

    @Test
    fun partialFailureDoesNotPostponeBackgroundWork() = runBlocking {
        assertFalse(rescanner { false }.rescan())
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun exceptionAndCancellationDoNotRestartSchedule() = runBlocking {
        for (failure in listOf(IllegalStateException("failed"), CancellationException("cancelled"))) {
            try {
                rescanner { throw failure }.rescan()
                fail("Scan should throw")
            } catch (actual: Exception) {
                assertSame(failure, actual)
            }
        }
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun emptySelectionDoesNotScanOrRestart() = runBlocking {
        setVaults()
        assertFalse(rescanner { true }.rescan())
        assertTrue(scans.isEmpty())
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun newlySelectedVaultPreventsPostponingItsScan() = runBlocking {
        assertTrue(rescanner { setVaults("a", "b", "c"); true }.rescan())
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun removingAllVaultsDuringScanPassesEmptySettingsToSchedulerForCancellation() = runBlocking {
        assertTrue(rescanner { setVaults(); true }.rescan())
        assertTrue(scheduled.single().first.vaultUris.isEmpty())
    }
}
