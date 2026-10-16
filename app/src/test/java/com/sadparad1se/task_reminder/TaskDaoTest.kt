package com.sadparad1se.task_reminder

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), TaskDatabase::class.java
    ).build()
    private val dao = database.taskDao()

    @Before
    fun seedTasks() = runBlocking {
        dao.upsert(task("No priority", null))
        dao.upsert(task("High priority", "High"))
        dao.upsert(task("Low priority", "low"))
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun noExclusionsIncludeTasksWithAndWithoutPriorities() = runBlocking {
        assertEquals(setOf("No priority", "High priority", "Low priority"), visibleTitles())
    }

    @Test
    fun excludingAPriorityKeepsUnprioritizedTasksAndOtherPriorities() = runBlocking {
        assertEquals(setOf("No priority", "Low priority"), visibleTitles(listOf("high")))
    }

    @Test
    fun excludingEveryNamedPriorityStillKeepsUnprioritizedTasks() = runBlocking {
        assertEquals(setOf("No priority"), visibleTitles(listOf("high", "low")))
    }

    private suspend fun visibleTitles(excluded: List<String> = emptyList()): Set<String> = dao.observeTasks(
        excludedPriorities = excluded,
        excludedPrioritiesEmpty = excluded.isEmpty(),
        includeUndated = true,
        includeUpcoming = true,
        includeCurrent = true,
        includeOverdue = true,
        now = "2026-10-16T12:00:00"
    ).first().map { it.title }.toSet()

    private fun task(title: String, priority: String?) = StoredTask(
        vaultUri = "content://vault/example",
        fileUri = "content://tasks/$title",
        filePath = "$title.md",
        fileName = "$title.md",
        title = title,
        excerpt = "",
        status = null,
        statusColor = null,
        priority = priority,
        priorityColor = null,
        priorityWeight = null,
        scheduled = null,
        due = null,
        dateCreated = null,
        dateModified = null,
        tags = "task",
        lastModified = null
    )
}
