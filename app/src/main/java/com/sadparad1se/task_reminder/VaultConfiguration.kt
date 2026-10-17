package com.sadparad1se.task_reminder

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.json.JSONObject

const val DefaultObsidianConfigurationFolder = ".obsidian"

internal object VaultPreferences {
    val uris = stringSetPreferencesKey("vault_uris")
    val configurationFolders = stringPreferencesKey("vault_configuration_folders")

    fun folders(preferences: Preferences): Map<String, String> {
        val json = JSONObject(preferences[configurationFolders] ?: "{}")
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    fun encode(folders: Map<String, String>): String = JSONObject(folders).toString()
}

/** Gives every previously selected vault an explicit configuration directory before settings are read. */
internal object VaultConfigurationMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val folders = VaultPreferences.folders(currentData)
        return currentData[VaultPreferences.uris].orEmpty().any { it !in folders }
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val folders = VaultPreferences.folders(currentData).toMutableMap()
        currentData[VaultPreferences.uris].orEmpty().forEach {
            folders.putIfAbsent(it, DefaultObsidianConfigurationFolder)
        }
        return currentData.toMutablePreferences().apply {
            this[VaultPreferences.configurationFolders] = VaultPreferences.encode(folders)
        }
    }

    override suspend fun cleanUp() = Unit
}
