package com.bernaferrari.bravedns.net.go

import com.bernaferrari.bravedns.database.CountryConfig
import org.junit.Assert.*
import org.junit.Test

class ProxyNetworkPolicyTest {
    @Test fun emptyWifiPolicyAllowsKnownWifiWithoutItsPrivateName() {
        val policy = ProxyNetworkPolicy(false, true, "")
        assertTrue(policy.allows(false, true, ""))
        assertFalse(policy.allows(true, false, ""))
        assertFalse(policy.allows(false, false, ""))
        assertTrue(ProxyNetworkPolicy(false, true, "[ ]").allows(false, true, ""))
    }
    @Test fun namedWifiPolicyDoesNotTreatUnavailableNameAsMatch() {
        val policy = ProxyNetworkPolicy(false, true, "[{\"name\":\"Home\",\"type\":\"equal_exact\"}]")
        assertFalse(policy.allows(false, true, ""))
        assertFalse(policy.allows(false, true, "<unknown ssid>"))
    }
    @Test fun relayInheritsAutoAndMissingAutoNeverBecomesUnrestricted() {
        val country = CountryConfig("country", "XX", key = "country", hopEnabled = true)
        val auto = CountryConfig("AUTO", "", key = "AUTO", mobileOnly = true)
        val policy = requireNotNull(ProxyNetworkPolicy.forRpn(country, auto))
        assertFalse(policy.allows(false, true, "Home"))
        assertTrue(policy.allows(true, false, ""))
        assertNull(ProxyNetworkPolicy.forRpn(country, null))
        country.hopEnabled = false
        assertTrue(requireNotNull(ProxyNetworkPolicy.forRpn(country, null)).allows(false, true, "Home"))
    }
    @Test fun malformedNonemptySsidPolicyCannotTurnIntoAnyWifi() {
        val policy = ProxyNetworkPolicy(false, true, "corrupt policy")
        assertFalse(policy.allows(false, true, ""))
        assertFalse(policy.allows(false, true, "Home"))
    }

}
