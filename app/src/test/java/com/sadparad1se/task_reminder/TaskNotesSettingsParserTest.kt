package com.sadparad1se.task_reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskNotesSettingsParserTest {
    private val parser = TaskNotesSettingsParser()

    /** Verifies the TaskNotes tasks folder is normalized from plugin JSON. */
    @Test
    fun parsesAndNormalizesTasksFolder() {
        val settings = parser.parse(
            """
                {
                  "tasksFolder": "/Tasks/Inbox/"
                }
            """.trimIndent()
        )

        assertEquals("Tasks/Inbox", settings.tasksFolder)
    }

    /** Verifies missing or blank TaskNotes tasksFolder fails fast. */
    @Test
    fun rejectsMissingTasksFolder() {
        assertThrows(IllegalStateException::class.java) {
            parser.parse("{ \"tasksFolder\": \"   \" }")
        }
    }

    /** Verifies completed statuses are stored case-insensitively. */
    @Test
    fun parsesCompletedStatuses() {
        val settings = parser.parse(
            """
                {
                  "tasksFolder": "Tasks",
                  "customStatuses": [
                    { "value": "Done", "isCompleted": true },
                    { "value": "Next", "isCompleted": false },
                    { "value": "  Cancelled  ", "isCompleted": true }
                  ]
                }
            """.trimIndent()
        )

        assertTrue(settings.isCompletedStatus("done"))
        assertTrue(settings.isCompletedStatus("CANCELLED"))
        assertFalse(settings.isCompletedStatus("next"))
        assertFalse(settings.isCompletedStatus(null))
    }

    /** Verifies status colors are parsed only when they are valid hex colors. */
    @Test
    fun parsesValidStatusColors() {
        val settings = parser.parse(
            """
                {
                  "tasksFolder": "Tasks",
                  "customStatuses": [
                    { "value": "Todo", "color": "#112233" },
                    { "value": "Done", "color": "#AA112233" },
                    { "value": "Invalid", "color": "red" }
                  ]
                }
            """.trimIndent()
        )

        assertEquals("#112233", settings.colorForStatus("todo"))
        assertEquals("#AA112233", settings.colorForStatus("DONE"))
        assertNull(settings.colorForStatus("invalid"))
    }

    /** Verifies priority colors and weights are parsed from TaskNotes custom priorities. */
    @Test
    fun parsesPriorityColorsAndWeights() {
        val settings = parser.parse(
            """
                {
                  "tasksFolder": "Tasks",
                  "customPriorities": [
                    { "value": "High", "color": "#ff0000", "weight": 100 },
                    { "value": "Low", "color": "not-a-color", "weight": -5 }
                  ]
                }
            """.trimIndent()
        )

        assertEquals("#ff0000", settings.colorForPriority("HIGH"))
        assertNull(settings.colorForPriority("low"))
        assertEquals(100, settings.weightForPriority("high"))
        assertEquals(-5, settings.weightForPriority("LOW"))
    }
}
