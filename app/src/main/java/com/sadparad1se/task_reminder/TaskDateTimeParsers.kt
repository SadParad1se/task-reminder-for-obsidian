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
        parseOffsetDateTimeOrNull()
            ?.atZoneSameInstant(zone)
            ?.toInstant()
            ?.toEpochMilli()
    }.getOrNull() ?: runCatching {
        parseLocalDateTimeOrNull()
            ?.atZone(zone)
            ?.toInstant()
            ?.toEpochMilli()
    }.getOrNull()
}

/** Parses display dates without shifting local times through daylight-saving transitions. */
internal fun String.toTaskNotesLocalDateTimeOrNull(
    dateOnlyTime: LocalTime = LocalTime.MIDNIGHT,
    zone: ZoneId = ZoneId.systemDefault()
): LocalDateTime? {
    return parseLocalDateTimeOrNull()
        ?: runCatching { parseOffsetDateTimeOrNull()?.atZoneSameInstant(zone)?.toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE).atTime(dateOnlyTime) }.getOrNull()
}

private fun String.parseLocalDateTimeOrNull(): LocalDateTime? =
    runCatching { LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()

private fun String.parseOffsetDateTimeOrNull(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }.getOrNull()
