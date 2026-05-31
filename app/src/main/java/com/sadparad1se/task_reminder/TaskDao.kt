package com.sadparad1se.task_reminder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room DAO for stored TaskNotes task rows. */
@Dao
interface TaskDao {
    /** Observes active tasks after applying priority exclusions and time bucket toggles. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE (:excludedPrioritiesEmpty OR lower(priority) NOT IN (:excludedPriorities))
            AND (
                (:includeUndated AND scheduled IS NULL AND due IS NULL)
                OR (:includeUpcoming AND scheduled IS NOT NULL AND datetime(scheduled) > datetime(:now))
                OR (
                    :includeOverdue
                    AND due IS NOT NULL
                    AND datetime(CASE WHEN length(due) = 10 THEN due || 'T23:59:59' ELSE due END) < datetime(:now)
                )
                OR (
                    :includeCurrent
                    AND (scheduled IS NULL OR datetime(scheduled) <= datetime(:now))
                    AND (
                        due IS NULL
                        OR datetime(CASE WHEN length(due) = 10 THEN due || 'T23:59:59' ELSE due END) >= datetime(:now)
                    )
                    AND (scheduled IS NOT NULL OR due IS NOT NULL)
                )
            )
        ORDER BY
            CASE WHEN priorityWeight IS NULL THEN 1 ELSE 0 END ASC,
            priorityWeight DESC,
            COALESCE(scheduled, '9999-12-31T23:59:59') ASC,
            COALESCE(CASE WHEN length(due) = 10 THEN due || 'T23:59:59' ELSE due END, '9999-12-31T23:59:59') ASC,
            title ASC
        """
    )
    fun observeTasks(
        excludedPriorities: List<String>,
        excludedPrioritiesEmpty: Boolean,
        includeUndated: Boolean,
        includeUpcoming: Boolean,
        includeCurrent: Boolean,
        includeOverdue: Boolean,
        now: String
    ): Flow<List<StoredTask>>

    /** Observes the distinct TaskNotes priorities available for filter buttons. */
    @Query(
        """
        SELECT priority FROM tasks
        WHERE priority IS NOT NULL AND trim(priority) != ''
        GROUP BY lower(priority)
        ORDER BY
            CASE WHEN max(priorityWeight) IS NULL THEN 1 ELSE 0 END ASC,
            max(priorityWeight) DESC,
            priority ASC
        """
    )
    fun observeTaskPriorities(): Flow<List<String>>

    /** Returns every stored task row for maintenance work such as notification rescheduling. */
    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<StoredTask>

    /** Returns all stored task rows sourced from one vault. */
    @Query("SELECT * FROM tasks WHERE vaultUri = :vaultUri")
    suspend fun getTasksByVaultUri(vaultUri: String): List<StoredTask>

    /** Returns a stored task by database id, or null when it no longer exists. */
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTask(taskId: Long): StoredTask?

    /** Returns a stored task by the source Markdown document URI. */
    @Query("SELECT * FROM tasks WHERE fileUri = :fileUri")
    suspend fun getByFileUri(fileUri: String): StoredTask?

    /** Inserts or replaces a stored task row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: StoredTask)

    /** Deletes the task row associated with a Markdown document URI. */
    @Query("DELETE FROM tasks WHERE fileUri = :fileUri")
    suspend fun deleteByFileUri(fileUri: String)

    /** Deletes all task rows sourced from one vault. */
    @Query("DELETE FROM tasks WHERE vaultUri = :vaultUri")
    suspend fun deleteByVaultUri(vaultUri: String)
}
