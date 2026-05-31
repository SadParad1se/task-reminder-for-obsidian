package com.sadparad1se.task_reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskReminderTypeTest {
    /** Verifies alarm intent storage values map to reminder types. */
    @Test
    fun parsesStorageValues() {
        assertEquals(TaskReminderType.SCHEDULED, TaskReminderType.fromStorageValue("scheduled"))
        assertEquals(TaskReminderType.DUE, TaskReminderType.fromStorageValue("due"))
    }

    /** Verifies invalid alarm intent values default to the scheduled reminder type. */
    @Test
    fun defaultsToScheduled() {
        assertEquals(TaskReminderType.SCHEDULED, TaskReminderType.fromStorageValue(null))
        assertEquals(TaskReminderType.SCHEDULED, TaskReminderType.fromStorageValue("unsupported"))
    }
}
