package com.bernaferrari.bravedns.service

import org.junit.Assert.*
import org.junit.Test

class VpnSessionStateTest {
    @Test fun freshProcessIsStoppedAndExplicitStartDispatchesOnlyOnce() {
        val session = VpnSessionState<Any>()
        assertFalse(session.state.value)
        assertTrue(session.beginStart(false))
        assertTrue(session.state.value)
        assertFalse(session.beginStart(false))
        session.startFailed()
        assertFalse(session.state.value)
    }

    @Test fun queuedRestartSurvivesOldDestructionAndIgnoresStaleOwnerCallbacks() {
        val session = VpnSessionState<Any>()
        val old = Any()
        val current = Any()
        session.created(old)
        session.cancelPending()
        assertTrue(session.queueStart())
        assertFalse(session.queueStart())
        session.destroyed(old)
        assertTrue(session.state.value)
        assertTrue(session.beginStart(true))
        session.created(current)
        session.destroyed(old)
        assertTrue(session.state.value)
        assertTrue(session.mayStart(current))
        session.destroyed(current)
        assertFalse(session.state.value)
    }

    @Test fun stopCancelsAQueuedStartBeforeItCanDispatch() {
        val session = VpnSessionState<Any>()
        session.queueStart()
        session.cancelPending()
        assertFalse(session.state.value)
        assertFalse(session.beginStart(true))
    }

    @Test fun stopBeforeOnCreateRejectsTheAlreadyDispatchedCommand() {
        val session = VpnSessionState<Any>()
        val service = Any()
        session.beginStart(false)
        session.cancelPending()
        assertFalse(session.state.value)
        session.created(service)
        assertFalse(session.state.value)
        assertFalse(session.mayStart(service))
    }

    @Test fun renewedStartReusesAnOutstandingCanceledCommand() {
        val session = VpnSessionState<Any>()
        session.beginStart(false)
        session.cancelPending()
        assertFalse(session.beginStart(false))
        assertTrue(session.state.value)
        val service = Any()
        session.created(service)
        assertTrue(session.mayStart(service))
    }

    @Test fun duplicateStartForAnExistingServiceDoesNotLeaveAPhantomRequest() {
        val session = VpnSessionState<Any>()
        val service = Any()
        session.created(service)
        assertFalse(session.beginStart(false))
        session.destroyed(service)
        assertFalse(session.state.value)
    }
}
