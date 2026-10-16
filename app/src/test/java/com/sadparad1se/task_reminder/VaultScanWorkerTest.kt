package com.sadparad1se.task_reminder

import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultScanWorkerTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun noSelectedVaultsCompletesSuccessfully() = runBlocking {
        val worker = TestListenableWorkerBuilder<VaultScanWorker>(context).build()
        assertEquals(Result.success(), worker.doWork())
    }

    @Test
    fun cancellationWhileLoadingSettingsDoesNotBecomeARetry() = runBlocking {
        val worker = TestListenableWorkerBuilder<VaultScanWorker>(context).build()
        var result: Result? = null
        launch(start = CoroutineStart.UNDISPATCHED) {
            coroutineContext.cancel()
            result = worker.doWork()
        }.join()
        assertNull("A canceled worker must propagate cancellation instead of returning retry", result)
    }
}
