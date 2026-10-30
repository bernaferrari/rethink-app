package com.bernaferrari.bravedns.net.go

import com.bernaferrari.bravedns.data.SmartDnsApplyStatus
import com.bernaferrari.bravedns.data.SmartDnsProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmartDnsProfileControllerTest {
    @Test fun nativeAcknowledgmentPrecedesTenSecondSettlingWindow() = runTest {
        val events = mutableListOf<SmartDnsApplyStatus>()
        val ack = CompletableDeferred<Unit>()
        var refreshes = 0
        val controller = SmartDnsProfileController(this, { SmartDnsProfile.Family }, { true }, { true }, {},
            { refreshes++; if (refreshes == 1) ack.await() }, events::add, { throw it })
        val application = async { controller.apply() }
        runCurrent()
        assertTrue(events.last() is SmartDnsApplyStatus.Queued)
        advanceTimeBy(15_000); runCurrent()
        assertFalse(events.any { it is SmartDnsApplyStatus.Active })
        ack.complete(Unit); application.await(); runCurrent()
        assertTrue(events.last() is SmartDnsApplyStatus.Settling)
        advanceTimeBy(9_999); runCurrent()
        assertFalse(events.any { it is SmartDnsApplyStatus.Active })
        advanceTimeBy(1); runCurrent()
        assertEquals(SmartDnsApplyStatus.Active(SmartDnsProfile.Family), events.last())
        assertEquals(2, refreshes)
        controller.close()
    }

    @Test fun rapidChangesCancelStaleCompletionAndDuplicatesKeepOriginalDeadline() = runTest {
        var selected = SmartDnsProfile.Family
        val events = mutableListOf<SmartDnsApplyStatus>()
        val installs = mutableListOf<SmartDnsProfile>()
        val controller = SmartDnsProfileController(this, { selected }, { true }, { true }, installs::add, {}, events::add, { throw it })
        controller.apply(); runCurrent()
        advanceTimeBy(5_000)
        selected = SmartDnsProfile.Privacy
        controller.apply(); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertFalse(events.any { it is SmartDnsApplyStatus.Active })
        controller.apply()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(SmartDnsApplyStatus.Active(SmartDnsProfile.Privacy), events.last())
        assertEquals(listOf(SmartDnsProfile.Family, SmartDnsProfile.Privacy), installs)
        controller.close()
    }

    @Test fun failedNativeSetterIsVisibleAndSameSelectionRetries() = runTest {
        var works = false
        val events = mutableListOf<SmartDnsApplyStatus>()
        val controller = SmartDnsProfileController(this, { SmartDnsProfile.Family }, { true }, { true },
            { check(works) }, {}, events::add, {})
        controller.apply()
        assertEquals(SmartDnsApplyStatus.Failed(SmartDnsProfile.Family), events.last())
        works = true
        controller.apply(); runCurrent(); advanceTimeBy(10_000); runCurrent()
        assertEquals(SmartDnsApplyStatus.Active(SmartDnsProfile.Family), events.last())
        controller.close()
    }

    @Test fun otherDnsResetsNativeNoneAndClosePreventsDelayedActiveStatus() = runTest {
        var enabled = true
        val events = mutableListOf<SmartDnsApplyStatus>()
        val installs = mutableListOf<SmartDnsProfile>()
        val controller = SmartDnsProfileController(this, { SmartDnsProfile.Family }, { enabled }, { true }, installs::add, {}, events::add, { throw it })
        controller.apply(); runCurrent()
        enabled = false
        controller.apply()
        assertEquals(SmartDnsProfile.None, installs.last())
        assertEquals(SmartDnsApplyStatus.Saved, events.last())
        advanceTimeBy(10_000); runCurrent()
        assertFalse(events.any { it is SmartDnsApplyStatus.Active })
        enabled = true
        controller.apply(); runCurrent()
        controller.close(); advanceTimeBy(10_000); runCurrent()
        assertEquals(SmartDnsApplyStatus.Saved, events.last())
        controller.apply()
        assertEquals(3, installs.size)
    }
    @Test fun ownerCancellationClearsLiveStatusAndRejectsNewApplications() = runTest {
        val job = SupervisorJob()
        val owner = CoroutineScope(coroutineContext + job)
        val events = mutableListOf<SmartDnsApplyStatus>()
        var installs = 0
        val controller = SmartDnsProfileController(owner, { SmartDnsProfile.Family }, { true }, { true },
            { installs++ }, {}, events::add, { throw it })
        controller.apply(); runCurrent()
        owner.cancel(); runCurrent()
        assertEquals(SmartDnsApplyStatus.Saved, events.last())
        controller.apply(); advanceTimeBy(10_000); runCurrent()
        assertEquals(1, installs)
        assertEquals(SmartDnsApplyStatus.Saved, events.last())
    }

    @Test fun defaultNoneCanBecomeActiveAfterOtherDnsAndAfterRepeatedModeSwitches() = runTest {
        var enabled = false
        val events = mutableListOf<SmartDnsApplyStatus>()
        val controller = SmartDnsProfileController(this, { SmartDnsProfile.None }, { enabled }, { true }, {}, {}, events::add, { throw it })
        controller.apply()
        assertEquals(SmartDnsApplyStatus.Saved, events.last())
        repeat(2) {
            enabled = true
            controller.apply(); runCurrent()
            assertEquals(SmartDnsApplyStatus.Settling(SmartDnsProfile.None), events.last())
            advanceTimeBy(10_000); runCurrent()
            assertEquals(SmartDnsApplyStatus.Active(SmartDnsProfile.None), events.last())
            enabled = false
            controller.apply()
            assertEquals(SmartDnsApplyStatus.Saved, events.last())
        }
        controller.close()
    }

    @Test fun closeWaitsForInFlightNativeWorkWithoutPublishingLateAcknowledgment() = runTest {
        val ack = CompletableDeferred<Unit>()
        val events = mutableListOf<SmartDnsApplyStatus>()
        val controller = SmartDnsProfileController(this, { SmartDnsProfile.Family }, { true }, { true }, {},
            { ack.await() }, events::add, { throw it })
        val application = async { controller.apply() }
        runCurrent()
        val closing = async { controller.close() }
        runCurrent()
        assertFalse(closing.isCompleted)
        assertEquals(SmartDnsApplyStatus.Saved, events.last())
        ack.complete(Unit)
        application.await(); closing.await()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(SmartDnsApplyStatus.Saved, events.last())
        assertFalse(events.any { it is SmartDnsApplyStatus.Active || it is SmartDnsApplyStatus.Settling })
    }

    @Test fun disconnectedNativeSessionCannotFinishItsPendingProfile() = runTest {
        var owns = true
        val events = mutableListOf<SmartDnsApplyStatus>()
        val controller = SmartDnsProfileController(this, { SmartDnsProfile.Family }, { true }, { owns }, {}, {}, events::add, { throw it })
        controller.apply(); runCurrent()
        owns = false
        advanceTimeBy(10_000); runCurrent()
        assertEquals(SmartDnsApplyStatus.Saved, events.last())
        controller.close()
    }

}
