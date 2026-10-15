package com.sadparad1se.task_reminder

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultScanWorkSchedulerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val hour = 3_600_000L
    private var clock = 1_800_000_000_000L
    private val settings = AppSettings(vaultUris = listOf("a"))
    private val workers = mutableListOf<HoldingWorker>()
    private lateinit var manager: WorkManager
    private lateinit var scheduler: VaultScanWorkScheduler

    @Before
    fun initialize() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setClock { clock }
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker {
                        return HoldingWorker(appContext, workerParameters).also { workers.add(it) }
                    }
                })
                .build()
        )
        manager = WorkManager.getInstance(context)
        scheduler = VaultScanWorkScheduler(context)
    }

    @After
    fun close() {
        manager.cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun work(): WorkInfo = manager.getWorkInfosForUniqueWork(VaultScanWorkScheduler.UniqueWorkName).get().single()

    private suspend fun finishFirstBackgroundScan(): WorkInfo {
        scheduler.schedule(settings).await()
        assertEquals(WorkInfo.State.RUNNING, work().state)
        workers.single().completion.set(ListenableWorker.Result.success())
        assertEquals(WorkInfo.State.ENQUEUED, work().state)
        return work()
    }

    @Test
    fun manualScanRestartsNextRunAndLaterManualScanMovesItAgain() = runBlocking {
        val original = finishFirstBackgroundScan()
        assertEquals(clock + hour, original.nextScheduleTimeMillis)
        clock += hour / 2
        scheduler.restartAfterManualScan(settings, clock)
        assertEquals(original.id, work().id)
        assertEquals(clock + hour, work().nextScheduleTimeMillis)
        clock += hour / 4
        scheduler.restartAfterManualScan(settings, clock)
        assertEquals(clock + hour, work().nextScheduleTimeMillis)
        assertEquals(1, workers.size)
    }

    @Test
    fun ordinarySchedulingPreservesManualOverride() = runBlocking {
        finishFirstBackgroundScan()
        clock += hour / 2
        scheduler.restartAfterManualScan(settings, clock)
        val next = work().nextScheduleTimeMillis
        VaultScanWorkScheduler(context).schedule(settings).await()
        assertEquals(next, work().nextScheduleTimeMillis)
    }

    @Test
    fun nextBackgroundRunConsumesOverrideAndResumesNormalInterval() = runBlocking {
        finishFirstBackgroundScan()
        clock += hour / 2
        scheduler.restartAfterManualScan(settings, clock)
        clock += hour
        WorkManagerTestInitHelper.getTestDriver(context)!!.setPeriodDelayMet(work().id)
        assertEquals(WorkInfo.State.RUNNING, work().state)
        workers.last().completion.set(ListenableWorker.Result.success())
        assertEquals(WorkInfo.State.ENQUEUED, work().state)
        assertEquals(clock + hour, work().nextScheduleTimeMillis)
        scheduler.schedule(settings).await()
        assertEquals(clock + hour, work().nextScheduleTimeMillis)
    }

    @Test
    fun explicitFrequencyChangeClearsOverride() = runBlocking {
        val initial = clock
        finishFirstBackgroundScan()
        clock += hour / 2
        scheduler.restartAfterManualScan(settings, clock)
        scheduler.schedule(settings.copy(scanFrequency = ScanFrequency.EVERY_6_HOURS), clearNextScanOverride = true).await()
        assertEquals(initial + 6 * hour, work().nextScheduleTimeMillis)
    }

    @Test
    fun runningWorkerKeepsItsIdentityAndReceivesOverrideForFollowingRun() = runBlocking {
        scheduler.schedule(settings).await()
        val original = work()
        val worker = workers.single()
        assertEquals(WorkInfo.State.RUNNING, original.state)
        clock += hour / 2
        scheduler.restartAfterManualScan(settings, clock)
        assertEquals(original.id, work().id)
        assertEquals(WorkInfo.State.RUNNING, work().state)
        assertFalse(worker.isStopped)
        worker.completion.set(ListenableWorker.Result.success())
        assertEquals(clock + hour, work().nextScheduleTimeMillis)
    }

    @Test
    fun noExistingJobCreatesOneWithDelayedFirstRunAndNoVaultsCancelIt() = runBlocking {
        scheduler.restartAfterManualScan(settings, clock)
        assertEquals(WorkInfo.State.ENQUEUED, work().state)
        assertEquals(clock + hour, work().nextScheduleTimeMillis)
        assertTrue(workers.isEmpty())
        scheduler.restartAfterManualScan(settings.copy(vaultUris = emptyList()), clock)
        assertEquals(WorkInfo.State.CANCELLED, work().state)
    }

    private class HoldingWorker(context: Context, params: WorkerParameters) : ListenableWorker(context, params) {
        lateinit var completion: CallbackToFutureAdapter.Completer<Result>
        override fun startWork(): ListenableFuture<Result> = CallbackToFutureAdapter.getFuture {
            completion = it
            "Controlled background scan"
        }
    }
}
