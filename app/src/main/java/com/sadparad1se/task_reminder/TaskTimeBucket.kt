package com.sadparad1se.task_reminder

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Selects whether a task is undated, scheduled for later, active now, or past due. */
enum class TaskTimeBucket(val label: String) {
    UNDATED("Undated"),
    UPCOMING("Upcoming"),
    CURRENT("Current"),
    OVERDUE("Overdue")
}

/** Classifies a task for its card color; upcoming takes precedence over overdue. */
internal fun TaskListItem.timeBucket(
    now: LocalDateTime = LocalDateTime.now(),
    zone: ZoneId = ZoneId.systemDefault()
): TaskTimeBucket {
    val scheduledDateTime = scheduled?.toTaskNotesLocalDateTimeOrNull(zone = zone)
    val dueDateTime = due?.toTaskNotesLocalDateTimeOrNull(dateOnlyTime = LocalTime.MAX, zone = zone)

    return when {
        scheduledDateTime == null && dueDateTime == null -> TaskTimeBucket.UNDATED
        scheduledDateTime != null && scheduledDateTime > now -> TaskTimeBucket.UPCOMING
        dueDateTime != null && dueDateTime < now -> TaskTimeBucket.OVERDUE
        else -> TaskTimeBucket.CURRENT
    }
}
