/*
Copyright 2020 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.celzero.bravedns.service

/**
 * Represents the state of the VPN connection.
 * Enhanced to provide computed properties for status evaluation.
 */
class VpnState(requested: Boolean, on: Boolean, connectionState: BraveVPNService.State?, server: String?) {

    var activationRequested = false

    // Whether the VPN is running.  When this is true a key icon is showing in the status bar.
    var on = false

    // Whether we have a connection to a DOH server, and if so, whether the connection is ready or
    // has recently been failing.
    var connectionState: BraveVPNService.State? = null

    // The server we are connected to, or null if we are not connected.
    var serverName: String? = null

    init {
        this.activationRequested = requested
        this.on = on
        this.connectionState = connectionState
        this.serverName = server
    }

    // Computed properties for easier state evaluation
    val isWorking: Boolean
        get() = on && connectionState == BraveVPNService.State.WORKING

    val isFailing: Boolean
        get() = connectionState in listOf(
            BraveVPNService.State.APP_ERROR,
            BraveVPNService.State.DNS_ERROR,
            BraveVPNService.State.DNS_SERVER_DOWN,
            BraveVPNService.State.NO_INTERNET
        )

    val isNew: Boolean
        get() = connectionState == BraveVPNService.State.NEW

    val isPaused: Boolean
        get() = connectionState == BraveVPNService.State.PAUSED

    val hasValidConnection: Boolean
        get() = on && (connectionState == BraveVPNService.State.WORKING || connectionState == BraveVPNService.State.NEW)

    val statusText: String
        get() = when {
            isPaused -> "Paused"
            isFailing -> "Failing"
            isWorking -> "Protected"
            isNew -> "Starting"
            on -> "Connecting"
            else -> "Disconnected"
        }
}
