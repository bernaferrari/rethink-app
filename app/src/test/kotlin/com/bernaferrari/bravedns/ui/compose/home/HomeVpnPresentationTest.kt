package com.bernaferrari.bravedns.ui.compose.home

import com.bernaferrari.bravedns.service.BraveVPNService
import com.bernaferrari.bravedns.service.VpnState
import org.junit.Assert.*
import org.junit.Test

class HomeVpnPresentationTest {
    @Test fun savedActivationWithoutALiveServiceShowsStoppedAndPreservesBootIntent() {
        val saved = VpnState(true, false, null, null)
        val result = homeVpnPresentation(saved, sessionActive = false, noNetwork = false)
        assertFalse(result.active)
        assertEquals(HomeProtectionStatus.Exposed, result.status)
        assertTrue(result.failing)
        assertTrue(saved.activationRequested)
    }

    @Test fun staleNetworkAndConnectionStatusCannotMaskAStoppedService() {
        val state = VpnState(true, false, BraveVPNService.State.WORKING, null)
        val result = homeVpnPresentation(state, sessionActive = false, noNetwork = true)
        assertFalse(result.active)
        assertEquals(HomeProtectionStatus.Exposed, result.status)
    }

    @Test fun freshStartBeforeServiceCreationShowsWaitingEvenBeforeSavedFlagChanges() {
        val state = VpnState(false, false, null, null)
        val result = homeVpnPresentation(state, sessionActive = true, noNetwork = false)
        assertTrue(result.active)
        assertEquals(HomeProtectionStatus.Waiting, result.status)
    }

    @Test fun liveTunnelKeepsItsProtectionAndEchStatus() {
        val state = VpnState(true, true, BraveVPNService.State.WORKING, "resolver", true)
        val result = homeVpnPresentation(state, sessionActive = true, noNetwork = false)
        assertTrue(result.active)
        assertEquals(HomeProtectionStatus.UltraSecure, result.status)
        assertFalse(result.failing)
    }

    @Test fun activeTunnelStillReportsNetworkAndResolverFailures() {
        val state = VpnState(true, true, BraveVPNService.State.DNS_ERROR, null)
        assertEquals(HomeProtectionStatus.Failing, homeVpnPresentation(state, true, false).status)
        assertEquals(HomeProtectionStatus.NoNetwork, homeVpnPresentation(state, true, true).status)
    }

    @Test fun pausedTunnelShowsPausedNotFailing() {
        val state = VpnState(true, true, BraveVPNService.State.PAUSED, null)
        val result = homeVpnPresentation(state, sessionActive = true, noNetwork = false)
        assertTrue(result.active)
        assertEquals(HomeProtectionStatus.Paused, result.status)
        assertFalse(result.failing)
        // Paused wins over a missing network: the hero shows the pause the user chose.
        assertEquals(HomeProtectionStatus.Paused, homeVpnPresentation(state, true, true).status)
    }

    @Test fun pausedStateWithoutAServiceIsStillStopped() {
        val state = VpnState(true, false, BraveVPNService.State.PAUSED, null)
        assertEquals(HomeProtectionStatus.Exposed, homeVpnPresentation(state, false, false).status)
    }
}
