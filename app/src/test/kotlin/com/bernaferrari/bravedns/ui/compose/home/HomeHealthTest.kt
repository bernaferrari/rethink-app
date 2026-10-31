package com.bernaferrari.bravedns.ui.compose.home

import com.bernaferrari.bravedns.net.doh.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHealthTest {
    private val clear = HomeEnvironment()

    @Test fun nothingWrongShowsNoBanner() {
        assertNull(homeHealthIssue(clear, active = true, paused = false, dnsFailing = false))
        assertNull(homeHealthIssue(clear, active = false, paused = false, dnsFailing = false))
    }

    @Test fun anotherAlwaysOnVpnShowsEvenWhenOffBecauseItBlocksStarting() {
        val env = HomeEnvironment(otherAlwaysOnVpn = true, privateDnsActive = true)
        assertEquals(HomeHealthIssue.OtherAlwaysOnVpn, homeHealthIssue(env, active = false, paused = false, dnsFailing = false))
        assertEquals(HomeHealthIssue.OtherAlwaysOnVpn, homeHealthIssue(env, active = true, paused = false, dnsFailing = true))
    }

    @Test fun privateDnsMattersOnlyWhileProtectionIsOnAndOutranksDnsFailing() {
        val env = HomeEnvironment(privateDnsActive = true)
        assertNull(homeHealthIssue(env, active = false, paused = false, dnsFailing = false))
        assertEquals(HomeHealthIssue.PrivateDnsOverride, homeHealthIssue(env, active = true, paused = false, dnsFailing = true))
    }

    @Test fun dnsFailingNeverHidesBehindProtectedButIsQuietWhilePausedOrOff() {
        assertEquals(HomeHealthIssue.DnsFailing, homeHealthIssue(clear, active = true, paused = false, dnsFailing = true))
        assertNull(homeHealthIssue(clear, active = true, paused = true, dnsFailing = true))
        assertNull(homeHealthIssue(clear, active = false, paused = false, dnsFailing = true))
    }

    @Test fun successCardWaitsForProtectionAndComesBeforeTheNotificationAsk() {
        val env = HomeEnvironment(canAskForNotifications = true)
        assertNull(homeCard(env, protectedOn = false, protectedCardArmed = true))
        assertEquals(HomeCard.Protected, homeCard(env, protectedOn = true, protectedCardArmed = true))
        assertEquals(HomeCard.Notifications, homeCard(env, protectedOn = true, protectedCardArmed = false))
        assertNull(homeCard(clear, protectedOn = true, protectedCardArmed = false))
    }

    @Test fun missingPreferredTransportAndServerErrorsCountAsFailing() {
        assertTrue(isDnsFailing(null))
        listOf(
            Transaction.Status.TRANSPORT_ERROR,
            Transaction.Status.NO_RESPONSE,
            Transaction.Status.BAD_RESPONSE,
            Transaction.Status.CLIENT_ERROR,
            Transaction.Status.INTERNAL_ERROR,
        ).forEach { assertTrue(it.name, isDnsFailing(it.id)) }
    }

    @Test fun workingNoInternetAndBadQueriesAreNotAResolverFailure() {
        listOf(
            Transaction.Status.START,
            Transaction.Status.COMPLETE,
            Transaction.Status.SEND_FAIL,
            Transaction.Status.BAD_QUERY,
        ).forEach { assertFalse(it.name, isDnsFailing(it.id)) }
    }

    @Test fun keepoutDnsLineRenamesPresetsAndLeavesOtherProvidersAlone() {
        val presets = mapOf("adult" to "Family", "plus" to "Custom")
        assertEquals("Keepout · Default", homeDnsLabel("RDNS Default", true, "Keepout", presets))
        assertEquals("Keepout · Family", homeDnsLabel("RDNS Adult", true, "Keepout", presets))
        assertEquals("Keepout · Custom", homeDnsLabel("RDNS Plus", true, "Keepout", presets))
        assertEquals("Keepout · My blocklists", homeDnsLabel("My blocklists", true, "Keepout", presets))
        assertEquals("Keepout", homeDnsLabel("RDNS", true, "Keepout", presets))
        assertEquals("Cloudflare", homeDnsLabel("Cloudflare", false, "Keepout", presets))
    }
}
