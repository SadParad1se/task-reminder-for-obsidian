package com.sadparad1se.task_reminder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultConfigurationTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun migrationFillsMissingFoldersWithoutReplacingCustomChoicesAndIsIdempotent() = runBlocking {
        val original = mutablePreferencesOf(
            VaultPreferences.uris to setOf("default", "custom"),
            VaultPreferences.configurationFolders to VaultPreferences.encode(mapOf("custom" to ".obsidianHandy1")),
            stringPreferencesKey("unrelated") to "preserved"
        )
        assertTrue(VaultConfigurationMigration.shouldMigrate(original))
        val migrated = VaultConfigurationMigration.migrate(original)
        assertEquals(mapOf("default" to ".obsidian", "custom" to ".obsidianHandy1"), VaultPreferences.folders(migrated))
        assertEquals("preserved", migrated[stringPreferencesKey("unrelated")])
        assertFalse(VaultConfigurationMigration.shouldMigrate(migrated))
        assertEquals(migrated, VaultConfigurationMigration.migrate(migrated))
    }

    @Test
    fun migrationRunsBeforeReadingSettingsAndPersistsAcrossReopening() = runBlocking {
        val file = File(context.cacheDir, "migration-${UUID.randomUUID()}.preferences_pb")
        val legacyJob = SupervisorJob()
        val legacy = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(legacyJob + Dispatchers.IO), produceFile = { file }
        )
        legacy.edit { it[VaultPreferences.uris] = setOf("vault") }
        legacyJob.cancelAndJoin()

        val migrationJob = SupervisorJob()
        val migratedStore = PreferenceDataStoreFactory.create(
            migrations = listOf(VaultConfigurationMigration),
            scope = CoroutineScope(migrationJob + Dispatchers.IO), produceFile = { file }
        )
        try {
            val settings = SettingsRepository(context, migratedStore).settingsFlow.first()
            assertEquals(mapOf("vault" to ".obsidian"), settings.vaultConfigurationFolders)
        } finally {
            migrationJob.cancelAndJoin()
        }

        val reopenedJob = SupervisorJob()
        val reopened = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(reopenedJob + Dispatchers.IO), produceFile = { file }
        )
        try {
            assertEquals(mapOf("vault" to ".obsidian"), VaultPreferences.folders(reopened.data.first()))
        } finally {
            reopenedJob.cancelAndJoin()
        }
    }

    @Test
    fun additionsOverridesResetAndRemovalPersistExplicitIndependentFolders() = runBlocking {
        try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { File(context.cacheDir, "configuration-${UUID.randomUUID()}.preferences_pb") }
        )
        val repository = SettingsRepository(context, store)
        try {
            repository.addVaultUri("default")
            repository.addVaultUri("custom", ".obsidianHandy1")
            assertEquals(mapOf("default" to ".obsidian", "custom" to ".obsidianHandy1"),
                repository.settingsFlow.first().vaultConfigurationFolders)
            repository.updateVaultConfigurationFolder("custom", "Phone settings")
            assertEquals("Phone settings", repository.settingsFlow.first().vaultConfigurationFolders.getValue("custom"))
            repository.updateVaultConfigurationFolder("custom", ".obsidian")
            assertEquals(".obsidian", VaultPreferences.folders(store.data.first()).getValue("custom"))
            repository.removeVaultUri("custom")
            assertEquals(listOf("default"), repository.settingsFlow.first().vaultUris)
            assertEquals(mapOf("default" to ".obsidian"), VaultPreferences.folders(store.data.first()))
            repository.updateVaultConfigurationFolder("custom", "removed")
            assertFalse("custom" in repository.settingsFlow.first().vaultConfigurationFolders)
        } finally {
            job.cancelAndJoin()
        }
    }
}
