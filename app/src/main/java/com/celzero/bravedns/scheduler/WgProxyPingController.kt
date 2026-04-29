package com.celzero.bravedns.scheduler

import Logger
import Logger.LOG_TAG_PROXY
import com.celzero.bravedns.service.ProxyManager.ID_WG_BASE
import com.celzero.bravedns.service.VpnController
import com.celzero.firestack.backend.Backend
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class WgProxyPingController(private val scope: CoroutineScope) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val intervalMs = 60_000L
    private val durationMs = 5 * 60 * 1000L

    fun startPing(proxyId: String, continuous: Boolean) {
        jobs.remove(proxyId)?.cancel()

        val job =
            scope.launch(Dispatchers.IO + CoroutineName("ping-$proxyId")) {
                run(proxyId, continuous)
            }

        jobs[proxyId] = job
        Logger.vv(LOG_TAG_PROXY, "initiated ping job for $proxyId, continuous? $continuous")
    }

    fun stopPing(proxyId: String) {
        jobs.remove(proxyId)?.cancel()
        Logger.vv(LOG_TAG_PROXY, "cancelled ping job for $proxyId")
    }

    fun stopAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        Logger.vv(LOG_TAG_PROXY, "cancelled all ping jobs")
    }

    fun isRunning(proxyId: String): Boolean {
        return jobs.containsKey(proxyId)
    }

    private suspend fun run(proxyId: String, continuous: Boolean) {
        val startTime = System.currentTimeMillis()
        var nextTick = alignToNextInterval(startTime)

        while (currentCoroutineContext().isActive) {

            val now = System.currentTimeMillis()
            val delayMs = nextTick - now

            if (delayMs > 0) {
                delay(delayMs)
            }

            launchPing(proxyId)

            // schedule next tick
            nextTick += intervalMs

            // exit if timed mode
            if (!continuous) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= durationMs) break
            }
        }

        jobs.remove(proxyId)
    }

    private fun alignToNextInterval(now: Long): Long {
        return ((now / intervalMs) + 1) * intervalMs
    }

    private fun launchPing(proxyId: String) {
        scope.launch(Dispatchers.IO + CoroutineName("ping-$proxyId")) {
            try {
                pingProxy(proxyId)
            } catch (e: Exception) {
                Logger.w(LOG_TAG_PROXY, "ping failed: $proxyId at ${System.currentTimeMillis()}; err: ${e.message}")
            }
        }
    }

    private suspend fun pingProxy(proxyId: String) {
        if (proxyId.startsWith(ID_WG_BASE)) {
            VpnController.initiateWgPing(proxyId)
            Logger.vv(LOG_TAG_PROXY, "ping triggered: $proxyId at ${System.currentTimeMillis()}")
        } else if (proxyId.startsWith(Backend.RpnWin)) {
            VpnController.initiateRpnPing(proxyId)
            Logger.vv(LOG_TAG_PROXY, "ping triggered: $proxyId at ${System.currentTimeMillis()}")
        }
    }
}
