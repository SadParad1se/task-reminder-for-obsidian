package com.sadparad1se.task_reminder

/** Builds a stable Android id/request code that keeps reminder types independent. */
fun taskReminderStableId(taskId: Long, reminderType: TaskReminderType): Int {
    return ((taskId * TaskReminderType.entries.size + reminderType.ordinal) and Int.MAX_VALUE.toLong()).toInt()
}
