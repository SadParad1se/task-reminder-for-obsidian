package com.sadparad1se.task_reminder

/** Distinguishes the independent reminder alarms that can be scheduled for one task. */
enum class TaskReminderType(val storageValue: String, val label: String) {
    SCHEDULED("scheduled", "Scheduled"),
    DUE("due", "Due");

    companion object {
        /** Converts an alarm intent value back to a reminder type. */
        fun fromStorageValue(value: String?): TaskReminderType {
            return entries.firstOrNull { it.storageValue == value } ?: SCHEDULED
        }
    }
}
