package com.bernaferrari.bravedns.service

import android.app.Application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class VpnStartGateFailureTest {
    @Test fun teardownFailureReleasesThePendingRequest() = runTest {
        val gate = VpnStartGate(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        var failures = 0
        var starts = 0
        gate.afterStopped({ error("native teardown failed") }, onFailure = { failures++ }) { starts++ }
        assertEquals(1, failures)
        assertEquals(0, starts)
    }

    @Test fun lateFailureOfACanceledRequestCannotClearItsReplacement() = runTest {
        val gate = VpnStartGate(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        val oldRelease = CompletableDeferred<Unit>()
        val newRelease = CompletableDeferred<Unit>()
        var failures = 0
        var starts = 0
        gate.afterStopped({
            withContext(NonCancellable) { oldRelease.await(); error("late native teardown failure") }
        }, onFailure = { failures++ }) { starts++ }
        gate.afterStopped({ newRelease.await() }, onFailure = { failures++ }) { starts++ }
        oldRelease.complete(Unit)
        assertEquals(0, failures)
        newRelease.complete(Unit)
        assertEquals(1, starts)
    }
}
