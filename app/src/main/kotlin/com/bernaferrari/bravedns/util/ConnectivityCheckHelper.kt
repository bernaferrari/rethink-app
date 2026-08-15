package com.bernaferrari.bravedns.util

import Logger
import Logger.LOG_TAG_CONNECTION
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.system.ErrnoException
import android.system.OsConstants.ECONNREFUSED
import com.bernaferrari.bravedns.service.ConnectionMonitor.Companion.netId
import com.bernaferrari.bravedns.service.ConnectionMonitor.NetworkProperties
import com.bernaferrari.bravedns.service.ConnectionMonitor.ProbeResult
import com.bernaferrari.bravedns.service.ProbeController
import com.bernaferrari.bravedns.service.VpnController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object ConnectivityCheckHelper {

    private const val CONN_TYPE = "auto"
    private const val TAG = "ConnCheckHelper"
    private const val DEFAULT_TIMEOUT = 500 // ms
    private const val HTTP_PORT = 80
    private const val DNS_PORT = 53

    /**
     * Configuration class for probe operations
     */
    data class ProbeConfig(
        val network: Network?,
        val useKotlinChecks: Boolean,
        val timeout: Int = DEFAULT_TIMEOUT
    ) {
        // Lazily initialize the ProbeController to avoid multiple creations
        val probeController: ProbeController by lazy {
            createProbeController(network)
        }
    }

    /**
     * Enum representing different probe modes
     */
    enum class ProbeMode {
        MANUAL, AUTO
    }

    /**
     * Generic probe result wrapper
     */
    sealed class ConnectivityResult {
        data class Success(val capabilities: NetworkCapabilities?) : ConnectivityResult()
        data class Failure(val reason: String) : ConnectivityResult()
    }

    /**
     * Creates a ProbeController based on network configuration
     */
    private fun createProbeController(nw: Network?): ProbeController {
        return if (nw != null) {
            ProbeController(nw.networkHandle, nw)
        } else {
            // For active network, protect socket to default network instead of binding
            ProbeController(-1, null)
        }
    }

    /**
     * Generic logging function for probe results
     */
    private fun logProbeResult(
        operation: String,
        id: String,
        target: String,
        success: Boolean,
        nw: Network?
    ) {
        Logger.v(
            LOG_TAG_CONNECTION,
            "$TAG $operation, id($id): $target, ok? $success, nw: ${nw?.networkHandle}, netid: ${netId(nw?.networkHandle)}"
        )
    }

    /**
     * Generic fallback check with Kotlin-based reachability
     */
    private suspend fun performFallbackCheck(
        config: ProbeConfig,
        target: String
    ): Boolean {
        return if (config.useKotlinChecks) {
            if (config.network != null) {
                isReachableTcpUdp(config.network, target)
            } else {
                isReachable(target)
            }
        } else {
            false
        }
    }

    /**
     * Enhanced probe connectivity with better error handling and logging
     */
    private suspend fun probeConnectivity(
        probes: Collection<String>,
        config: ProbeConfig
    ): Boolean {
        if (probes.isEmpty()) return false

        return probes.any { ipOrUrl ->
            if (ipOrUrl.isEmpty()) return@any false

            val id = "probe:${netId(config.network?.networkHandle)}:$ipOrUrl"
            var success = VpnController.performConnectivityCheck(config.probeController, id, ipOrUrl)

            if (!success) {
                success = performFallbackCheck(config, ipOrUrl)
            }

            logProbeResult("probeConnectivity", id, ipOrUrl, success, config.network)
            success
        }
    }

    /**
     * Generic probe execution wrapper with async handling
     */
    private suspend fun executeProbe(
        operation: String,
        config: ProbeConfig,
        probeFunction: suspend () -> Boolean,
        fallbackTarget: String? = null
    ): Boolean = coroutineScope {
        val deferred = async(Dispatchers.IO) { probeFunction() }
        var result = deferred.await()

        if (!result && config.useKotlinChecks && fallbackTarget != null) {
            result = performFallbackCheck(config, fallbackTarget)
            logProbeResult(operation, "fallback", fallbackTarget, result, config.network)
        }

        result
    }

    /**
     * Probes connectivity in automatic mode for a given scheme and protocol
     */
    suspend fun probeConnectivityInAutoMode(
        nw: Network? = null,
        scheme: String,
        protocol: String,
        ipOrUrl: Collection<String>,
        useKotlinChecks: Boolean
    ): Boolean {
        val config = ProbeConfig(nw, useKotlinChecks)
        val mode = "$CONN_TYPE:$scheme:$protocol"
        val id = "probe:${netId(nw?.networkHandle)}:$mode"

        return executeProbe(
            operation = "probeConnectivityInAutoMode",
            config = config,
            probeFunction = {
                VpnController.performAutoConnectivityCheck(config.probeController, id, mode)
            },
            fallbackTarget = ipOrUrl.firstOrNull()
        ).also { result ->
            logProbeResult("probeConnectivityInAutoMode", id, mode, result, nw)
        }
    }

    /**
     * Probes connectivity in manual mode
     */
    suspend fun probeConnectivityInManualMode(
        ipOrUrls: Collection<String>,
        nw: Network? = null,
        useKotlinChecks: Boolean
    ): Boolean {
        val config = ProbeConfig(nw, useKotlinChecks)
        val id = "probe:${netId(nw?.networkHandle)}:$ipOrUrls"

        return executeProbe(
            operation = "probeConnectivityInManualMode",
            config = config,
            probeFunction = {
                probeConnectivity(ipOrUrls, config)
            },
            fallbackTarget = ipOrUrls.firstOrNull()
        ).also { result ->
            logProbeResult("probeConnectivityInManualMode", id, ipOrUrls.toString(), result, nw)
        }
    }

    /**
     * Enhanced probeIpOrUrl with better error handling
     */
    suspend fun probeIpOrUrl(
        ipOrUrl: String,
        nws: Set<NetworkProperties>,
        activeNwCapabilities: NetworkCapabilities?,
        useKotlinChecks: Boolean
    ): ProbeResult {
        val config = ProbeConfig(null, useKotlinChecks)

        if (nws.isEmpty()) {
            val success = probeConnectivity(listOf(ipOrUrl), config) ||
                    performFallbackCheck(config, ipOrUrl)

            Logger.i(LOG_TAG_CONNECTION, "$TAG probeIpOrUrl: $ipOrUrl, ok? $success, cap: $activeNwCapabilities")
            return ProbeResult(ipOrUrl, success, activeNwCapabilities)
        }

        return nws.firstNotNullOfOrNull { nw ->
            val networkConfig = ProbeConfig(nw.network, useKotlinChecks)
            val success = probeConnectivity(listOf(ipOrUrl), networkConfig) ||
                    performFallbackCheck(networkConfig, ipOrUrl)

            if (success) {
                Logger.i(
                    LOG_TAG_CONNECTION,
                    "$TAG probeIpOrUrl: $ipOrUrl, ok? $success, netid: ${netId(nw.network.networkHandle)}, cap: ${nw.capabilities}"
                )
                ProbeResult(ipOrUrl, true, nw.capabilities)
            } else {
                null
            }
        } ?: ProbeResult(ipOrUrl, false, null)
    }

    /**
     * Generic TCP connection checker
     */
    private suspend fun checkTcpConnection(
        nw: Network?,
        host: String,
        port: Int,
        portName: String
    ): Boolean {
        var socket: Socket? = null
        try {
            socket = Socket()
            val socketAddress = InetSocketAddress(host, port)
            nw?.bindSocket(socket)
            VpnController.protectSocket(socket)
            socket.connect(socketAddress, DEFAULT_TIMEOUT)

            val isConnected = socket.isConnected
            val isBound = socket.isBound

            Logger.d(
                LOG_TAG_CONNECTION,
                "$TAG tcp$portName: $host, ${nw?.networkHandle}, netid: ${netId(nw?.networkHandle)}, connected: $isConnected, bound: $isBound"
            )
            return true
        } catch (e: IOException) {
            Logger.w(LOG_TAG_CONNECTION, "$TAG err tcp$portName: ${e.message}", e)
            val cause = e.cause
            return cause is ErrnoException && cause.errno == ECONNREFUSED
        } catch (e: SecurityException) {
            Logger.w(LOG_TAG_CONNECTION, "$TAG err tcp$portName: ${e.message}", e)
            return false
        } catch (e: IllegalArgumentException) {
            Logger.w(LOG_TAG_CONNECTION, "$TAG err tcp$portName: ${e.message}", e)
            return false
        } finally {
            closeQuietly(socket)
        }
    }

    /**
     * Checks if the host is reachable via TCP/UDP on the given network
     */
    private suspend fun isReachableTcpUdp(nw: Network?, host: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            TrafficStats.setThreadStatsTag(Thread.currentThread().id.toIntOrDefault())
            val result = checkTcpConnection(nw, host, HTTP_PORT, "80") ||
                    checkTcpConnection(nw, host, DNS_PORT, "53")

            Logger.d(
                LOG_TAG_CONNECTION,
                "$TAG $host isReachable on network ${nw?.networkHandle}, netid: ${netId(nw?.networkHandle)}: $result"
            )
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Logger.w(LOG_TAG_CONNECTION, "$TAG err isReachableTcpUdp: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if the host is reachable using ICMP echo request
     */
    private suspend fun isReachable(host: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            TrafficStats.setThreadStatsTag(Thread.currentThread().id.toIntOrDefault())
            val result = InetAddress.getByName(host).isReachable(DEFAULT_TIMEOUT)
            Logger.d(LOG_TAG_CONNECTION, "$TAG $host isReachable via ICMP: $result")
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Logger.w(LOG_TAG_CONNECTION, "$TAG err isReachable: ${e.message}", e)
            false
        } catch (e: SecurityException) {
            Logger.w(LOG_TAG_CONNECTION, "$TAG err isReachable: ${e.message}", e)
            false
        } catch (e: IllegalArgumentException) {
            Logger.w(LOG_TAG_CONNECTION, "$TAG err isReachable: ${e.message}", e)
            false
        }
    }

    /**
     * Safe socket closing utility
     */
    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (e: IOException) {
            Logger.w(LOG_TAG_CONNECTION, "$TAG Error closing resource: ${e.message}", e)
        }
    }

    /**
     * Extension function for safe Long to Int conversion
     */
    private fun Long.toIntOrDefault(default: Int = 0): Int {
        return if (this in Int.MIN_VALUE..Int.MAX_VALUE) this.toInt() else default
    }

}
