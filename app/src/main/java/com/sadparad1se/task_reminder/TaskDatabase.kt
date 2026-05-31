package com.sadparad1se.task_reminder

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Room database that stores scanned TaskNotes tasks and vault scan status. */
@Database(
    entities = [StoredTask::class, VaultScanState::class],
    version = 10,
    exportSchema = false
)
abstract class TaskDatabase : RoomDatabase() {
    /** Returns database access methods for stored tasks. */
    abstract fun taskDao(): TaskDao

    /** Returns database access methods for vault scan status rows. */
    abstract fun vaultScanStateDao(): VaultScanStateDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        /** Returns the singleton Room database instance for the application. */
        fun getInstance(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "tasks.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
