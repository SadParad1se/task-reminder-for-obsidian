package com.sadparad1se.task_reminder

/** Selects whether a task is undated, scheduled for later, active now, or past due. */
enum class TaskTimeBucket(val label: String) {
    UNDATED("Undated"),
    UPCOMING("Upcoming"),
    CURRENT("Current"),
    OVERDUE("Overdue")
}
