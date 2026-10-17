package com.sadparad1se.task_reminder

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ForegroundScanStateTest {
    @Test
    fun repeatedActionsAreIgnoredWhileRunningAndCanRunAgainAfterCompletion() = runBlocking {
        val state = ForegroundScanState()
        val finish = CompletableDeferred<Unit>()
        var started = 0
        val scan = launch(start = CoroutineStart.UNDISPATCHED) {
            state.run {
                started++
                finish.await()
            }
        }
        assertTrue(state.isScanning)
        state.run { started++ }
        assertEquals(1, started)
        finish.complete(Unit)
        scan.join()
        assertFalse(state.isScanning)
        state.run { started++ }
        assertEquals(2, started)
    }

    @Test
    fun failureRestoresLoadingStateAndPropagatesTheError() = runBlocking {
        val state = ForegroundScanState()
        val failure = IllegalStateException("Scan failed")
        try {
            state.run { throw failure }
            fail("Failure must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertFalse(state.isScanning)
        var retried = false
        state.run { retried = true }
        assertTrue(retried)
    }

    @Test
    fun cancellationRestoresLoadingStateAndAllowsAnotherScan() = runBlocking {
        val state = ForegroundScanState()
        val scan = launch(start = CoroutineStart.UNDISPATCHED) {
            state.run { awaitCancellation() }
        }
        assertTrue(state.isScanning)
        scan.cancelAndJoin()
        assertFalse(state.isScanning)
        var retried = false
        state.run { retried = true }
        assertTrue(retried)
    }
}
