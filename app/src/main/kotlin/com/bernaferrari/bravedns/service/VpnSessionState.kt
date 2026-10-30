package com.bernaferrari.bravedns.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Current-process start requests and service ownership, independent of saved boot recovery intent. */
internal class VpnSessionState<T : Any> {
    private enum class Request { NONE, QUEUED, DISPATCHED, CANCELED }
    private var request = Request.NONE
    private var owner: T? = null
    private var canceledOwner: T? = null
    private val active = MutableStateFlow(false)
    val state = active.asStateFlow()

    @Synchronized fun queueStart(): Boolean {
        if (request == Request.QUEUED) return false
        request = Request.QUEUED
        publish()
        return true
    }

    /** True only when a new OS start command is needed; an outstanding canceled command is reused. */
    @Synchronized fun beginStart(resumingQueued: Boolean): Boolean {
        if (owner != null) return false
        if (request == Request.CANCELED && !resumingQueued) {
            request = Request.DISPATCHED
            publish()
            return false
        }
        val expected = if (resumingQueued) Request.QUEUED else Request.NONE
        if (request != expected) return false
        request = Request.DISPATCHED
        publish()
        return true
    }

    @Synchronized fun cancelPending() {
        canceledOwner = owner
        request = if (request == Request.DISPATCHED && owner == null) Request.CANCELED else Request.NONE
        publish()
    }

    @Synchronized fun startFailed() {
        request = Request.NONE
        publish()
    }

    @Synchronized fun created(service: T) {
        owner = service
        canceledOwner = if (request == Request.CANCELED) service else null
        request = Request.NONE
        publish()
    }

    @Synchronized fun mayStart(service: T): Boolean = owner === service && canceledOwner !== service

    @Synchronized fun destroyed(service: T) {
        if (owner !== service) return
        owner = null
        canceledOwner = null
        publish()
    }

    private fun publish() {
        active.value = (owner != null && owner !== canceledOwner) ||
            request == Request.QUEUED || request == Request.DISPATCHED
    }
}
