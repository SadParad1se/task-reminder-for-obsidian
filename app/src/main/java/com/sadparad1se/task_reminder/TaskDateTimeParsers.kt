package com.sadparad1se.task_reminder

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val NotificationTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Parses a persisted HH:mm notification time, falling back to 07:00 if invalid. */
fun parseNotificationTime(time: String): LocalTime {
    return runCatching { LocalTime.parse(time, NotificationTimeFormatter) }
        .getOrDefault(LocalTime.of(7, 0))
}

/** Parses TaskNotes date or date-time values into epoch milliseconds when possible. */
fun String.toTaskNotesEpochMillisOrNull(zone: ZoneId = ZoneId.systemDefault()): Long? {
    toTaskNotesDateTimeEpochMillisOrNull(zone)?.let { return it }
    runCatching {
        return LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }
    return null
}

/** Parses TaskNotes local or offset date-time values into epoch milliseconds when possible. */
fun String.toTaskNotesDateTimeEpochMillisOrNull(zone: ZoneId = ZoneId.systemDefault()): Long? {
    runCatching {
        return OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .atZoneSameInstant(zone)
            .toInstant()
            .toEpochMilli()
    }
    runCatching {
        return LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
    return null
}
