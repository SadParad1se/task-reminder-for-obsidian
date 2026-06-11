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
    return toTaskNotesDateTimeEpochMillisOrNull(zone)
        ?: runCatching {
            LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
}

/** Parses TaskNotes local or offset date-time values into epoch milliseconds when possible. */
fun String.toTaskNotesDateTimeEpochMillisOrNull(zone: ZoneId = ZoneId.systemDefault()): Long? {
    return runCatching {
        OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .atZoneSameInstant(zone)
            .toInstant()
            .toEpochMilli()
    }.getOrNull() ?: runCatching {
        LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}
