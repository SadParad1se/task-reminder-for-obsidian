package com.sadparad1se.task_reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    /** Verifies persisted scan frequency values map to their enum entries. */
    @Test
    fun scanFrequencyParsesStorageValues() {
        assertEquals(ScanFrequency.EVERY_15_MINUTES, ScanFrequency.fromStorageValue("every_15_minutes"))
        assertEquals(ScanFrequency.EVERY_30_MINUTES, ScanFrequency.fromStorageValue("every_30_minutes"))
        assertEquals(ScanFrequency.EVERY_HOUR, ScanFrequency.fromStorageValue("every_hour"))
        assertEquals(ScanFrequency.EVERY_6_HOURS, ScanFrequency.fromStorageValue("every_6_hours"))
        assertEquals(ScanFrequency.EVERY_12_HOURS, ScanFrequency.fromStorageValue("every_12_hours"))
    }

    /** Verifies unknown persisted scan frequency values fall back safely. */
    @Test
    fun scanFrequencyDefaultsToHourly() {
        assertEquals(ScanFrequency.EVERY_HOUR, ScanFrequency.fromStorageValue(null))
        assertEquals(ScanFrequency.EVERY_HOUR, ScanFrequency.fromStorageValue("unsupported"))
    }
}
