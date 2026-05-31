package com.sadparad1se.task_reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownTaskParserTest {
    private val parser = MarkdownTaskParser()

    /** Verifies inline tags and scalar TaskNotes metadata are parsed. */
    @Test
    fun parsesTaskNoteWithInlineTagsAndMetadata() {
        val parsedTaskNote = parser.parseTaskNote(
            markdown = """
                ---
                tags: [task, work]
                status: todo
                priority: high
                scheduled: 2026-05-25T09:30:00
                due: 2026-05-26
                dateCreated: 2026-05-20T08:00:00
                dateModified: 2026-05-21T10:00:00
                ---
                # Follow up

                Send the project update.
            """.trimIndent(),
            fileName = "Follow up.md"
        )

        requireNotNull(parsedTaskNote)
        assertEquals("Follow up", parsedTaskNote.title)
        assertEquals("Send the project update.", parsedTaskNote.excerpt)
        assertEquals("todo", parsedTaskNote.status)
        assertEquals("high", parsedTaskNote.priority)
        assertEquals("2026-05-25T09:30:00", parsedTaskNote.scheduled)
        assertEquals("2026-05-26", parsedTaskNote.due)
        assertEquals("2026-05-20T08:00:00", parsedTaskNote.dateCreated)
        assertEquals("2026-05-21T10:00:00", parsedTaskNote.dateModified)
        assertEquals(listOf("task", "work"), parsedTaskNote.tags)
    }

    /** Verifies YAML list tags are parsed as task tags. */
    @Test
    fun parsesTaskNoteWithMultilineTags() {
        val parsedTaskNote = parser.parseTaskNote(
            markdown = """
                ---
                tags:
                  - task
                  - home
                scheduled: 2026-05-25
                ---
                # Water plants
            """.trimIndent(),
            fileName = "Water plants.md"
        )

        requireNotNull(parsedTaskNote)
        assertEquals(listOf("task", "home"), parsedTaskNote.tags)
        assertEquals("2026-05-25", parsedTaskNote.scheduled)
    }

    /** Verifies notes without the TaskNotes task tag are ignored. */
    @Test
    fun rejectsNotesWithoutTaskTag() {
        val parsedTaskNote = parser.parseTaskNote(
            markdown = """
                ---
                tags: [note]
                ---
                # Reference
            """.trimIndent(),
            fileName = "Reference.md"
        )

        assertNull(parsedTaskNote)
    }

    /** Verifies notes without YAML frontmatter are ignored. */
    @Test
    fun rejectsNotesWithoutFrontmatter() {
        val parsedTaskNote = parser.parseTaskNote(
            markdown = "# Follow up\n\nSend the project update.",
            fileName = "Follow up.md"
        )

        assertNull(parsedTaskNote)
    }

    /** Verifies the Markdown filename is used as the title when no heading exists. */
    @Test
    fun usesFilenameWhenHeadingIsMissing() {
        val parsedTaskNote = parser.parseTaskNote(
            markdown = """
                ---
                tags: task
                ---

                Body text.
            """.trimIndent(),
            fileName = "Inbox task.md"
        )

        requireNotNull(parsedTaskNote)
        assertEquals("Inbox task", parsedTaskNote.title)
        assertEquals("Body text.", parsedTaskNote.excerpt)
    }

    /** Verifies task tag matching is case-insensitive and duplicate tags are removed. */
    @Test
    fun parsesTaskTagCaseInsensitivelyAndRemovesDuplicates() {
        val parsedTaskNote = parser.parseTaskNote(
            markdown = """
                ---
                tags: [Task, work, work]
                ---
                # Case-insensitive task
            """.trimIndent(),
            fileName = "Case-insensitive task.md"
        )

        requireNotNull(parsedTaskNote)
        assertEquals(listOf("Task", "work"), parsedTaskNote.tags)
    }

    /** Verifies quoted scalar YAML values are unwrapped. */
    @Test
    fun parsesQuotedScalarProperties() {
        val parsedTaskNote = parser.parseTaskNote(
            markdown = """
                ---
                tags: 'task'
                status: "next"
                priority: 'medium'
                ---
                # Quoted values
            """.trimIndent(),
            fileName = "Quoted values.md"
        )

        requireNotNull(parsedTaskNote)
        assertEquals("next", parsedTaskNote.status)
        assertEquals("medium", parsedTaskNote.priority)
        assertEquals(listOf("task"), parsedTaskNote.tags)
    }

    /** Verifies invalid scheduled and due values are ignored instead of persisted. */
    @Test
    fun ignoresInvalidDateValues() {
        val parsedTaskNote = parser.parseTaskNote(
            markdown = """
                ---
                tags: task
                scheduled: tomorrow morning
                due: 2026-99-99
                ---
                # Invalid dates
            """.trimIndent(),
            fileName = "Invalid dates.md"
        )

        requireNotNull(parsedTaskNote)
        assertNull(parsedTaskNote.scheduled)
        assertNull(parsedTaskNote.due)
    }

    /** Verifies ISO offset date-time values are accepted for date fields. */
    @Test
    fun acceptsOffsetDateTimes() {
        val parsedTaskNote = parser.parseTaskNote(
            markdown = """
                ---
                tags: task
                scheduled: 2026-05-25T09:30:00+02:00
                due: 2026-05-26T17:45:00Z
                ---
                # Offset dates
            """.trimIndent(),
            fileName = "Offset dates.md"
        )

        requireNotNull(parsedTaskNote)
        assertEquals("2026-05-25T09:30:00+02:00", parsedTaskNote.scheduled)
        assertEquals("2026-05-26T17:45:00Z", parsedTaskNote.due)
    }

    /** Verifies long excerpts are shortened for list display. */
    @Test
    fun shortensLongExcerpts() {
        val longExcerpt = "a".repeat(150)
        val parsedTaskNote = parser.parseTaskNote(
            markdown = """
                ---
                tags: task
                ---
                # Long excerpt
                $longExcerpt
            """.trimIndent(),
            fileName = "Long excerpt.md"
        )

        requireNotNull(parsedTaskNote)
        assertEquals("${"a".repeat(137)}...", parsedTaskNote.excerpt)
    }
}
