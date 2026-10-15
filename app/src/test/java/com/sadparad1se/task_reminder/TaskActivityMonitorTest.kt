package com.sadparad1se.task_reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskActivityMonitorTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = StaleTaskUpdateNotifier(context)
    private val storeFile = File(context.cacheDir, "activity-${UUID.randomUUID()}.preferences_pb")
    private var storeJob = SupervisorJob()
    private var store = createStore()
    private var settings = SettingsRepository(context, store)
    private var clock = 1_800_000_000_000L
    private var monitor = TaskActivityMonitor(settings, notifier) { clock }
    private val hour = 3_600_000L

    private fun createStore() = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(storeJob + Dispatchers.IO),
        produceFile = { storeFile }
    )

    @Before
    fun configureVaults() = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        selectVaults("a", "b")
    }

    @After
    fun closeStore() = runBlocking { storeJob.cancelAndJoin() }

    private suspend fun selectVaults(vararg vaults: String) {
        store.edit { it[stringSetPreferencesKey("vault_uris")] = vaults.toSet() }
    }

    private suspend fun scan(activityIn: Set<String> = emptySet(), failures: Set<String> = emptySet(), vaults: List<String> = listOf("a", "b")): Boolean {
        return monitor.scanVaults(vaults, { VaultScanResult(it !in failures, clock, it in activityIn) }, {})
    }

    private fun notification(): Notification = manager.activeNotifications.single {
        it.tag == StaleTaskUpdateNotifier.NotificationTag
    }.notification

    @Test
    fun initializesEmptyAndExistingVaultsWithoutUsingLegacyNotificationTime() = runBlocking {
        store.edit { it[longPreferencesKey("last_stale_task_update_notification_at")] = clock - 1 }
        assertTrue(scan())
        val initial = settings.settingsFlow.first()
        assertEquals(clock, initial.lastTaskActivityScanAt)
        assertEquals(0L, initial.lastStaleTaskUpdateNotificationAt)
        clock += hour
        scan()
        assertEquals(initial.lastTaskActivityScanAt, settings.settingsFlow.first().lastTaskActivityScanAt)
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    fun renewsAt24And48And72HoursWithoutDriftingFromDelayedScan() = runBlocking {
        scan()
        val baseline = clock
        clock = baseline + 24 * hour - 1
        scan()
        assertEquals(0, manager.activeNotifications.size)
        clock = baseline + 24 * hour
        scan()
        val first = notification()
        assertFalse(first.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        clock = baseline + 47 * hour
        scan()
        assertSame(first, notification())
        assertEquals(baseline + 24 * hour, settings.settingsFlow.first().lastStaleTaskUpdateNotificationAt)
        clock = baseline + 49 * hour
        scan()
        val second = notification()
        assertNotSame(first, second)
        clock = baseline + 72 * hour
        scan()
        assertNotSame(second, notification())
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(clock, settings.settingsFlow.first().lastStaleTaskUpdateNotificationAt)
    }

    @Test
    fun longDelayPostsOnlyOneReminderAndDismissalDoesNotRestartCycle() = runBlocking {
        scan()
        val baseline = clock
        clock += 73 * hour
        scan()
        assertEquals(1, manager.activeNotifications.size)
        notifier.cancel()
        clock += hour
        scan()
        assertEquals(0, manager.activeNotifications.size)
        clock = baseline + 96 * hour
        scan()
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test
    fun activityInEitherVaultResetsSharedTimerAndCancelsReminder() = runBlocking {
        scan()
        clock += 24 * hour
        scan()
        clock += hour
        scan(activityIn = setOf("b"))
        val resetAt = clock
        assertEquals(resetAt, settings.settingsFlow.first().lastTaskActivityScanAt)
        assertEquals(0, manager.activeNotifications.size)
        clock += 24 * hour - 1
        scan()
        assertEquals(0, manager.activeNotifications.size)
        clock++
        scan()
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test
    fun failuresDoNotInitializeTrackingOrConsumeActivityAndPartialScansDoNotNotify() = runBlocking {
        assertFalse(scan(failures = setOf("a", "b"), activityIn = setOf("a")))
        assertEquals(0L, settings.settingsFlow.first().lastTaskActivityScanAt)
        scan()
        val baseline = clock
        clock += 24 * hour
        scan(vaults = listOf("a"))
        assertEquals(0, manager.activeNotifications.size)
        assertFalse(scan(failures = setOf("b")))
        assertEquals(0, manager.activeNotifications.size)
        assertEquals(baseline, settings.settingsFlow.first().lastTaskActivityScanAt)
        scan(activityIn = setOf("a"), failures = setOf("b"))
        assertEquals(clock, settings.settingsFlow.first().lastTaskActivityScanAt)
        clock += 24 * hour
        scan()
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test
    fun blockedDeliveryIsRetriedWithoutAdvancingDeliveredWindow() = runBlocking {
        scan()
        clock += 24 * hour
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        scan()
        assertEquals(0L, settings.settingsFlow.first().lastStaleTaskUpdateNotificationAt)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(false)
        scan()
        assertEquals(0L, settings.settingsFlow.first().lastStaleTaskUpdateNotificationAt)
        shadowOf(manager).setNotificationsEnabled(true)
        manager.createNotificationChannel(NotificationChannel(
            StaleTaskUpdateNotifier.ChannelId, "Task update reminders", NotificationManager.IMPORTANCE_NONE
        ))
        scan()
        assertEquals(0L, settings.settingsFlow.first().lastStaleTaskUpdateNotificationAt)
        assertEquals(0, manager.activeNotifications.size)
        manager.getNotificationChannel(StaleTaskUpdateNotifier.ChannelId).importance = NotificationManager.IMPORTANCE_DEFAULT
        clock += hour
        scan()
        assertEquals(clock, settings.settingsFlow.first().lastStaleTaskUpdateNotificationAt)
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test
    fun disabledRemindersStillTrackActivityAndReenablingKeepsBaseline() = runBlocking {
        scan()
        clock += 24 * hour
        scan()
        settings.updateStaleTaskUpdateNotificationsEnabled(false)
        assertEquals(0, manager.activeNotifications.size)
        clock += hour
        scan(activityIn = setOf("a"))
        val resetAt = clock
        clock += 24 * hour
        scan()
        assertEquals(0, manager.activeNotifications.size)
        settings.updateStaleTaskUpdateNotificationsEnabled(true)
        scan()
        assertEquals(resetAt, settings.settingsFlow.first().lastTaskActivityScanAt)
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test
    fun restartPreservesBaselineAndPreviouslyDeliveredWindow() = runBlocking {
        scan()
        val baseline = clock
        clock += 25 * hour
        scan()
        val postedAt = clock
        storeJob.cancelAndJoin()
        storeJob = SupervisorJob()
        store = createStore()
        settings = SettingsRepository(context, store)
        monitor = TaskActivityMonitor(settings, notifier) { clock }
        clock = baseline + 26 * hour
        scan()
        assertEquals(baseline, settings.settingsFlow.first().lastTaskActivityScanAt)
        assertEquals(postedAt, settings.settingsFlow.first().lastStaleTaskUpdateNotificationAt)
        clock = baseline + 48 * hour
        scan()
        assertEquals(clock, settings.settingsFlow.first().lastStaleTaskUpdateNotificationAt)
    }

    @Test
    fun repositoryInstancesSerializeOverlappingScans() = runBlocking {
        scan()
        clock += 24 * hour
        var active = 0
        var maximumActive = 0
        val other = TaskActivityMonitor(settings, notifier) { clock }
        listOf(monitor, other).map { instance ->
            async {
                instance.scanVaults(listOf("a", "b"), {
                    active++
                    maximumActive = maxOf(maximumActive, active)
                    delay(10)
                    active--
                    VaultScanResult(true, clock)
                }, {})
            }
        }.awaitAll()
        assertEquals(1, maximumActive)
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test
    fun zeroBaselineAndClockRollbackNeverTriggerReminder() {
        assertFalse(isStaleTaskReminderDue(AppSettings(), clock))
        assertFalse(isStaleTaskReminderDue(AppSettings(lastTaskActivityScanAt = clock + hour), clock))
    }

    @Test
    fun removingFinalVaultClearsReminderAndRestartsBaselineWhenReadded() = runBlocking {
        initializeWorkManager()
        scan()
        clock += 24 * hour
        scan()
        settings.removeVaultUri("a")
        assertEquals(1, manager.activeNotifications.size)
        settings.removeVaultUri("b")
        assertEquals(0, manager.activeNotifications.size)
        assertEquals(0L, settings.settingsFlow.first().lastTaskActivityScanAt)
        assertEquals(0L, settings.settingsFlow.first().lastStaleTaskUpdateNotificationAt)
        monitor.scanVaults(listOf("a", "b"), { error("Removed vault must not be scanned") }, {})
        clock += hour
        settings.addVaultUri("a")
        scan(vaults = listOf("a"))
        assertEquals(clock, settings.settingsFlow.first().lastTaskActivityScanAt)
    }

    @Test
    fun addingVaultClearsItsOldScanCheckpointButKeepsOtherVaultTracking() = runBlocking {
        initializeWorkManager()
        scan()
        val baseline = clock
        val database = TaskDatabase.getInstance(context)
        database.vaultScanStateDao().upsert(VaultScanState("c", clock - hour))
        settings.addVaultUri("c")
        assertNull(database.vaultScanStateDao().get("c"))
        assertEquals(baseline, settings.settingsFlow.first().lastTaskActivityScanAt)
        clock += hour
        scan(activityIn = setOf("c"), vaults = listOf("c"))
        assertEquals(clock, settings.settingsFlow.first().lastTaskActivityScanAt)
        assertEquals(0, manager.activeNotifications.size)
    }

    private fun initializeWorkManager() {
        try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
    }
}
