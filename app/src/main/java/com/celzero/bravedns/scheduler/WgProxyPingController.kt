/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class WgProxyPingController(private val scope: CoroutineScope) {
    private val activeProxies = ConcurrentHashMap<String, PingConfig>()

    private val intervalMs = 60_000L
    private val durationMs = 5 * 60 * 1000L

    private var schedulerJob: Job? = null

    data class PingConfig(
        val startTime: Long,
        val continuous: Boolean,
        var running: Boolean = false // prevents overlap
    )

    fun startPing(proxyId: String, continuous: Boolean) {
        activeProxies[proxyId] = PingConfig(
            startTime = System.currentTimeMillis(),
            continuous = continuous
        )

        ensureScheduler()
        Logger.vv(LOG_TAG_PROXY, "started ping for $proxyId, continuous=$continuous")
    }

    fun stopPing(proxyId: String) {
        activeProxies.remove(proxyId)
        Logger.vv(LOG_TAG_PROXY, "stopped ping for $proxyId")

        if (activeProxies.isEmpty()) stopScheduler()
    }

    fun stopAll() {
        activeProxies.clear()
        stopScheduler()
        Logger.vv(LOG_TAG_PROXY, "stopped all ping jobs")
    }

    fun isRunning(proxyId: String): Boolean {
        return activeProxies.containsKey(proxyId)
    }

    private fun ensureScheduler() {
        if (schedulerJob?.isActive == true) return

        schedulerJob = scope.launch(Dispatchers.IO + CoroutineName("ping-scheduler")) {
            var nextTick = alignToNextInterval(System.currentTimeMillis())

            while (isActive && activeProxies.isNotEmpty()) {

                val delayMs = nextTick - System.currentTimeMillis()
                if (delayMs > 0) delay(delayMs)

                tick()

                nextTick += intervalMs
            }
        }
    }

    private fun stopScheduler() {
        schedulerJob?.cancel()
        schedulerJob = null
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()

        for ((proxyId, config) in activeProxies) {

            // skip if already running (prevents overlap)
            if (config.running) continue

            // check duration for non-continuous mode
            if (!config.continuous) {
                val elapsed = now - config.startTime
                if (elapsed >= durationMs) {
                    activeProxies.remove(proxyId)
                    continue
                }
            }

            config.running = true

            scope.launch(Dispatchers.IO + CoroutineName("ping-$proxyId")) {
                try {
                    pingProxy(proxyId)
                } catch (e: Exception) {
                    Logger.w(LOG_TAG_PROXY, "ping failed: $proxyId err=${e.message}")
                } finally {
                    activeProxies[proxyId]?.running = false
                }
            }
        }
    }

    private fun alignToNextInterval(now: Long): Long {
        return ((now / intervalMs) + 1) * intervalMs
    }

    private suspend fun pingProxy(proxyId: String) {
        when {
            proxyId.startsWith(ID_WG_BASE) -> {
                VpnController.initiateWgPing(proxyId)
            }

            proxyId.startsWith(Backend.RpnWin) -> {
                VpnController.initiateRpnPing(proxyId)
            }
        }

        Logger.vv(LOG_TAG_PROXY, "ping triggered: $proxyId at ${System.currentTimeMillis()}")
    }
}
