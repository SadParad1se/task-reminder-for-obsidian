package com.sadparad1se.task_reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TaskTimeBucketTest {
    private val now = LocalDateTime.of(2026, 10, 16, 12, 0)
    private val zone = ZoneId.of("Europe/Prague")

    @Test
    fun classifiesUndatedUpcomingCurrentAndOverdueTasks() {
        assertBucket(TaskTimeBucket.UNDATED)
        assertBucket(TaskTimeBucket.UPCOMING, scheduled = "2026-10-17")
        assertBucket(TaskTimeBucket.CURRENT, scheduled = "2026-10-16")
        assertBucket(TaskTimeBucket.OVERDUE, due = "2026-10-15")
    }

    @Test
    fun dateOnlyDueDatesRemainCurrentUntilTheEndOfTheDay() {
        assertBucket(TaskTimeBucket.CURRENT, due = "2026-10-16")
        val endOfDay = LocalDateTime.of(now.toLocalDate(), LocalTime.MAX)
        assertEquals(TaskTimeBucket.CURRENT, task(due = "2026-10-16").timeBucket(endOfDay, zone))
        assertEquals(TaskTimeBucket.OVERDUE, task(due = "2026-10-16").timeBucket(endOfDay.plusNanos(1), zone))
    }

    @Test
    fun upcomingCardColorStillTakesPrecedenceWhenDueDateIsAlreadyPast() {
        assertBucket(TaskTimeBucket.UPCOMING, scheduled = "2026-10-17", due = "2026-10-15")
    }

    @Test
    fun offsetTimesAreConvertedToTheDisplayZone() {
        assertBucket(TaskTimeBucket.CURRENT, scheduled = "2026-10-16T09:00:00Z")
        assertBucket(TaskTimeBucket.UPCOMING, scheduled = "2026-10-16T11:00:00Z")
    }

    @Test
    fun displayParsingPreservesLocalTimesInsideADaylightSavingGap() {
        assertEquals(
            LocalDateTime.of(2026, 3, 29, 2, 30),
            "2026-03-29T02:30:00".toTaskNotesLocalDateTimeOrNull(zone = zone)
        )
        assertNull("invalid".toTaskNotesLocalDateTimeOrNull(zone = zone))
    }

    private fun assertBucket(expected: TaskTimeBucket, scheduled: String? = null, due: String? = null) {
        assertEquals(expected, task(scheduled, due).timeBucket(now, zone))
    }

    private fun task(scheduled: String? = null, due: String? = null) =
        TaskListItem(title = "Example", scheduled = scheduled, due = due, excerpt = "")
}
