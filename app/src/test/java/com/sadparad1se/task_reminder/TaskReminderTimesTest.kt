package com.sadparad1se.task_reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TaskReminderTimesTest {
    private val zone = ZoneId.of("Europe/Prague")
    private val now = millis("2026-10-16T06:00:00Z")
    private val settings = AppSettings(
        scheduledDateDefaultNotificationTime = "09:30",
        dueDateDefaultNotificationTime = "18:00"
    )

    @Test
    fun dateOnlyRemindersUseTheirOwnConfiguredTimeInTheLocalZone() {
        assertEquals(
            millis("2026-10-16T07:30:00Z"),
            scheduledReminderTimeMillis("2026-10-16", settings, now, zone)
        )
        assertEquals(
            millis("2026-10-16T16:00:00Z"),
            dueReminderTimeMillis("2026-10-16", settings, now, zone)
        )
    }

    @Test
    fun localDateTimesUseTheLocalZoneAndIgnoreDateOnlyDefaults() {
        val expected = millis("2026-10-16T10:15:00Z")
        assertEquals(expected, scheduledReminderTimeMillis("2026-10-16T12:15:00", settings, now, zone))
        assertEquals(expected, dueReminderTimeMillis("2026-10-16T12:15:00", settings, now, zone))
    }

    @Test
    fun offsetDateTimesKeepTheirInstantAcrossZones() {
        val expected = millis("2026-10-16T09:00:00Z")
        for (localZone in listOf(zone, ZoneId.of("America/New_York"))) {
            assertEquals(expected, scheduledReminderTimeMillis("2026-10-16T12:00:00+03:00", settings, now, localZone))
            assertEquals(expected, dueReminderTimeMillis("2026-10-16T12:00:00+03:00", settings, now, localZone))
        }
    }

    @Test
    fun disabledScheduledDateTimesStillAllowDateOnlyReminders() {
        val disabled = settings.copy(scheduledDateTimeNotificationsEnabled = false)
        assertNull(scheduledReminderTimeMillis("2026-10-16T12:15:00", disabled, now, zone))
        assertNull(scheduledReminderTimeMillis("2026-10-16T12:15:00Z", disabled, now, zone))
        assertEquals(millis("2026-10-16T07:30:00Z"), scheduledReminderTimeMillis("2026-10-16", disabled, now, zone))
    }

    @Test
    fun disablingDueRemindersCoversBothDatesAndDateTimes() {
        val disabled = settings.copy(dueDateNotificationsEnabled = false)
        for (date in listOf("2026-10-16", "2026-10-16T12:15:00", "2026-10-16T12:15:00Z")) {
            assertNull(dueReminderTimeMillis(date, disabled, now, zone))
        }
    }

    @Test
    fun absentInvalidPastAndCurrentTimesDoNotProduceAlarms() {
        for (date in listOf(null, "", "invalid", "2026-02-30", "2026-10-15", "2026-10-16T08:00:00", "2026-10-16T05:59:59Z")) {
            assertNull(scheduledReminderTimeMillis(date, settings, now, zone))
            assertNull(dueReminderTimeMillis(date, settings, now, zone))
        }
    }

    @Test
    fun invalidDefaultTimesKeepTheExistingSevenAmFallback() {
        val invalid = settings.copy(
            scheduledDateDefaultNotificationTime = "invalid",
            dueDateDefaultNotificationTime = "invalid"
        )
        val earlier = millis("2026-10-16T00:00:00Z")
        assertEquals(millis("2026-10-16T05:00:00Z"), scheduledReminderTimeMillis("2026-10-16", invalid, earlier, zone))
        assertEquals(millis("2026-10-16T05:00:00Z"), dueReminderTimeMillis("2026-10-16", invalid, earlier, zone))
    }

    @Test
    fun daylightSavingGapAndRepeatedHourKeepTheirExistingResolution() {
        val earlier = millis("2026-01-01T00:00:00Z")
        assertEquals(
            millis("2026-03-29T01:30:00Z"),
            scheduledReminderTimeMillis("2026-03-29T02:30:00", settings, earlier, zone)
        )
        assertEquals(
            millis("2026-10-25T00:30:00Z"),
            scheduledReminderTimeMillis("2026-10-25T02:30:00+02:00", settings, earlier, zone)
        )
        assertEquals(
            millis("2026-10-25T01:30:00Z"),
            scheduledReminderTimeMillis("2026-10-25T02:30:00+01:00", settings, earlier, zone)
        )
    }

    private fun millis(value: String): Long = Instant.parse(value).toEpochMilli()
}
