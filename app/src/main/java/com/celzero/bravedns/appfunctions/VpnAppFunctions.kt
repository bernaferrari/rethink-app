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
package com.celzero.bravedns.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.celzero.bravedns.service.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AppFunctions that expose RethinkDNS VPN and firewall controls to on-device AI agents.
 */
class VpnAppFunctions {

    /** The current VPN and firewall protection status. */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class VpnStatus(
        /** Whether the VPN tunnel is currently active. */
        val isActive: Boolean,
        /** Human-readable connection status such as Protected, Paused, or Disconnected. */
        val status: String,
        /** Whether the user has requested VPN protection. */
        val activationRequested: Boolean,
        /** The connected DNS server name, or null when not connected. */
        val serverName: String?,
        /** Whether the firewall is temporarily paused. */
        val isPaused: Boolean,
    )

    /** The result of a VPN control action. */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class VpnActionResult(
        /** Whether the action completed successfully. */
        val success: Boolean,
        /** A human-readable result message. */
        val message: String,
        /** The VPN status after the action, if available. */
        val status: VpnStatus?,
    )

    /**
     * Returns the current VPN and firewall protection status.
     *
     * @param appFunctionContext The execution context.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getVpnStatus(appFunctionContext: AppFunctionContext): VpnStatus =
        withContext(Dispatchers.IO) {
            toVpnStatus(VpnController.state())
        }

    /**
     * Starts VPN and DNS firewall protection.
     * The user must have granted VPN permission beforehand.
     * Call [getVpnStatus] first to check whether protection is already active.
     *
     * @param appFunctionContext The execution context.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun startVpn(appFunctionContext: AppFunctionContext): VpnActionResult =
        withContext(Dispatchers.IO) {
            if (VpnController.hasTunnel()) {
                return@withContext VpnActionResult(
                    success = true,
                    message = "VPN protection is already active",
                    status = toVpnStatus(VpnController.state()),
                )
            }
            VpnController.start(appFunctionContext.context)
            VpnActionResult(
                success = true,
                message = "VPN protection started",
                status = toVpnStatus(VpnController.state()),
            )
        }

    /**
     * Stops VPN and DNS firewall protection.
     * Confirm with the user before calling this function because it removes network protection.
     *
     * @param appFunctionContext The execution context.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun stopVpn(appFunctionContext: AppFunctionContext): VpnActionResult =
        withContext(Dispatchers.IO) {
            if (!VpnController.hasTunnel()) {
                return@withContext VpnActionResult(
                    success = true,
                    message = "VPN protection is already stopped",
                    status = toVpnStatus(VpnController.state()),
                )
            }
            VpnController.stop("appfunction", appFunctionContext.context)
            VpnActionResult(
                success = true,
                message = "VPN protection stopped",
                status = toVpnStatus(VpnController.state()),
            )
        }

    /**
     * Temporarily pauses firewall protection while keeping the VPN service running.
     * Call [getVpnStatus] first to verify protection is active and not already paused.
     *
     * @param appFunctionContext The execution context.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun pauseVpn(appFunctionContext: AppFunctionContext): VpnActionResult =
        withContext(Dispatchers.IO) {
            if (!VpnController.hasTunnel()) {
                return@withContext VpnActionResult(
                    success = false,
                    message = "VPN is not active; nothing to pause",
                    status = toVpnStatus(VpnController.state()),
                )
            }
            if (VpnController.isAppPaused()) {
                return@withContext VpnActionResult(
                    success = true,
                    message = "Firewall is already paused",
                    status = toVpnStatus(VpnController.state()),
                )
            }
            VpnController.pauseApp()
            VpnActionResult(
                success = true,
                message = "Firewall protection paused",
                status = toVpnStatus(VpnController.state()),
            )
        }

    /**
     * Resumes firewall protection after a pause.
     * Call [getVpnStatus] first to verify the firewall is currently paused.
     *
     * @param appFunctionContext The execution context.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun resumeVpn(appFunctionContext: AppFunctionContext): VpnActionResult =
        withContext(Dispatchers.IO) {
            if (!VpnController.isAppPaused()) {
                return@withContext VpnActionResult(
                    success = true,
                    message = "Firewall is not paused",
                    status = toVpnStatus(VpnController.state()),
                )
            }
            VpnController.resumeApp()
            VpnActionResult(
                success = true,
                message = "Firewall protection resumed",
                status = toVpnStatus(VpnController.state()),
            )
        }

    private fun toVpnStatus(state: com.celzero.bravedns.service.VpnState): VpnStatus {
        return VpnStatus(
            isActive = state.on,
            status = state.statusText,
            activationRequested = state.activationRequested,
            serverName = state.serverName,
            isPaused = state.isPaused,
        )
    }
}