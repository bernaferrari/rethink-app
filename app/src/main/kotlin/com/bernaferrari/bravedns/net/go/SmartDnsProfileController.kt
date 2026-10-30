package com.bernaferrari.bravedns.net.go

import com.bernaferrari.bravedns.data.SmartDnsApplyStatus
import com.bernaferrari.bravedns.data.SmartDnsProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The pinned Plus resolver memoizes its ordered provider list for ten seconds. */
internal class SmartDnsProfileController(
    private val scope: CoroutineScope,
    private val desired: () -> SmartDnsProfile,
    private val smartEnabled: () -> Boolean,
    private val ownsSession: () -> Boolean,
    private val install: (SmartDnsProfile) -> Unit,
    private val refresh: suspend () -> Unit,
    private val publish: (SmartDnsApplyStatus) -> Unit,
    private val failure: (Exception) -> Unit,
) {
    private val mutex = Mutex()
    private var timer: Job? = null
    private var generation = 0L
    private var installed: SmartDnsProfile? = null
    private var acknowledged = false
    private var status: SmartDnsApplyStatus = SmartDnsApplyStatus.Saved
    @Volatile private var closed = false
    private val ownerCompletion = scope.coroutineContext[Job]?.invokeOnCompletion { cancel() }

    @Suppress("TooGenericExceptionCaught") // Generated JNI setters/resolver refresh can throw Exception; report it.
    suspend fun apply() = mutex.withLock {
        if (closed || !ownsSession()) { emit(SmartDnsApplyStatus.Saved); return@withLock }
        val active = smartEnabled()
        val profile = if (active) desired() else SmartDnsProfile.None
        if (isAcknowledged(profile, active)) {
            keepAcknowledgedStatus(active)
            return@withLock
        }
        generation++
        val token = generation
        timer?.cancel()
        acknowledged = false
        if (active) emit(SmartDnsApplyStatus.Queued(profile))
        try {
            install(profile)
            installed = profile
            refresh()
            if (!active) { emit(SmartDnsApplyStatus.Saved); acknowledged = true; return@withLock }
            if (!matches(profile)) return@withLock
            acknowledged = true
            settle(profile, token)
        } catch (cancelled: CancellationException) {
            emit(SmartDnsApplyStatus.Saved)
            throw cancelled
        } catch (error: Exception) {
            failure(error)
            if (active && matches(profile)) emit(SmartDnsApplyStatus.Failed(profile))
        }
    }

    private fun isAcknowledged(profile: SmartDnsProfile, active: Boolean) =
        installed == profile && acknowledged && (!active || status != SmartDnsApplyStatus.Saved)

    private fun keepAcknowledgedStatus(active: Boolean) {
        if (active) emit(status) else { timer?.cancel(); emit(SmartDnsApplyStatus.Saved) }
    }

    @Suppress("TooGenericExceptionCaught") // Same generated native boundary; cancellation propagates.
    private fun settle(profile: SmartDnsProfile, token: Long) {
        emit(SmartDnsApplyStatus.Settling(profile))
        timer = scope.launch {
            delay(SETTLING_MILLIS)
            mutex.withLock {
                if (token != generation) return@withLock
                if (!ownsSession() || !smartEnabled()) { emit(SmartDnsApplyStatus.Saved); return@withLock }
                if (!matches(profile)) return@withLock
                try {
                    refresh()
                    if (matches(profile)) emit(SmartDnsApplyStatus.Active(profile))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    acknowledged = false
                    failure(error)
                    if (matches(profile)) emit(SmartDnsApplyStatus.Failed(profile))
                }
            }
        }
    }

    @Synchronized private fun emit(value: SmartDnsApplyStatus) {
        if (closed && value != SmartDnsApplyStatus.Saved) return
        status = value
        publish(value)
    }

    private fun matches(profile: SmartDnsProfile) =
        !closed && ownsSession() && smartEnabled() && desired() == profile

    suspend fun close() {
        cancel()
        mutex.withLock { /* Await an in-flight native setter before replacement. */ }
    }

    private fun cancel() {
        closed = true
        timer?.cancel()
        ownerCompletion?.dispose()
        emit(SmartDnsApplyStatus.Saved)
    }

    companion object { const val SETTLING_MILLIS = 10_000L }
}
