package com.bernaferrari.bravedns.ui.compose.home

import com.bernaferrari.bravedns.service.BraveVPNService
import com.bernaferrari.bravedns.service.VpnState

internal data class HomeVpnPresentation(
    val active: Boolean,
    val status: HomeProtectionStatus,
    val failing: Boolean,
)

/** Saved activation intent authorizes boot recovery, but cannot prove a service survived process death. */
internal fun homeVpnPresentation(
    state: VpnState,
    sessionActive: Boolean,
    noNetwork: Boolean,
): HomeVpnPresentation {
    val active = state.on || sessionActive
    val status = when {
        !active -> HomeProtectionStatus.Exposed
        noNetwork -> HomeProtectionStatus.NoNetwork
        !state.on -> HomeProtectionStatus.Waiting
        state.connectionState == BraveVPNService.State.WORKING ||
            state.connectionState == BraveVPNService.State.NEW ->
            if (state.isEch) HomeProtectionStatus.UltraSecure else HomeProtectionStatus.Protected
        else -> HomeProtectionStatus.Failing
    }
    return HomeVpnPresentation(active, status,
        status != HomeProtectionStatus.Protected && status != HomeProtectionStatus.UltraSecure)
}
