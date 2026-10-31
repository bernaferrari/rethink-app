package com.bernaferrari.bravedns.ui.compose.dns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DnsChoiceModelTest {
    @Test
    fun pickingAnItemNeverMovesIt() {
        // The table sorts the selected endpoint first; after picking "c" it would jump to the top.
        val opened = listOf("a", "b", "c")
        val afterPick = listOf("c", "a", "b")
        assertEquals(opened, stableOrder(opened, afterPick))
    }

    @Test
    fun firstLoadTakesTheSourceOrderSoTheInUseItemLeads() {
        assertEquals(listOf("c", "a", "b"), stableOrder(emptyList(), listOf("c", "a", "b")))
    }

    @Test
    fun newItemsGoLastAndRemovedOnesDropOut() {
        assertEquals(listOf("a", "c", "d"), stableOrder(listOf("a", "b", "c"), listOf("d", "c", "a")))
    }

    @Test
    fun choiceFollowsTheStoredDnsType() {
        assertEquals(RethinkDnsChoice.Rethink, RethinkDnsSettingsState(isRethinkDnsConnected = true).dnsChoice())
        assertEquals(RethinkDnsChoice.Network, RethinkDnsSettingsState(isSystemDnsEnabled = true).dnsChoice())
        assertEquals(RethinkDnsChoice.Provider, RethinkDnsSettingsState(dnsType = RethinkDnsType.Dot).dnsChoice())
        // Smart DNS still answers; it is shown as such (from Advanced), never as another choice.
        assertEquals(RethinkDnsChoice.Automatic, RethinkDnsSettingsState(isSmartDnsEnabled = true, isRethinkDnsConnected = true).dnsChoice())
    }

    @Test
    fun presetsAreDisplayNamesOverTheStoredOnes() {
        assertEquals(RethinkDnsPreset.Default, RethinkDnsPreset.fromStoredName("RDNS Default"))
        assertEquals(RethinkDnsPreset.Family, RethinkDnsPreset.fromStoredName("RDNS Adult"))
        assertEquals(RethinkDnsPreset.SocialMedia, RethinkDnsPreset.fromStoredName(" RDNS Social Media "))
        assertEquals(RethinkDnsPreset.Custom, RethinkDnsPreset.fromStoredName("RDNS Plus"))
        assertNull(RethinkDnsPreset.fromStoredName("My endpoint"))
    }

    @Test
    fun connectedNameSplitsIntoNameAndEndpoint() {
        assertEquals("RDNS Default", connectedDnsDisplayName("RDNS Default, https://sky.rethinkdns.com/rec"))
        assertEquals("https://sky.rethinkdns.com/rec", connectedDnsEndpoint("RDNS Default, https://sky.rethinkdns.com/rec"))
        assertEquals("", connectedDnsEndpoint("Cloudflare"))
    }

    @Test
    fun serversGroupByProvider() {
        assertEquals(dnsProviderKey("Cloudflare"), dnsProviderKey("Cloudflare Family"))
        assertEquals(dnsProviderKey("Cleanbrowsing Family"), dnsProviderKey("CleanBrowsing Adult"))
        assertEquals("AdGuard", dnsProviderDisplayName("Adguard Family"))
        assertEquals("Quad9", dnsProviderDisplayName("Quad9 Secure"))
        assertEquals("Ibksturm", dnsProviderDisplayName("ibksturm"))
    }

    @Test
    fun expertSearchResultsOpenAdvanced() {
        assertFalse(isDnsAdvancedFocusKey(null))
        assertFalse(isDnsAdvancedFocusKey("dns_mode_rethink"))
        assertFalse(isDnsAdvancedFocusKey("dns_block_local"))
        assertTrue(isDnsAdvancedFocusKey("dns_mode_smart"))
        assertTrue(isDnsAdvancedFocusKey("dns_filter_alg"))
        assertTrue(isDnsAdvancedFocusKey("dns_advanced_cache"))
        assertTrue(isDnsAdvancedFocusKey("dns_block_periodic_updates"))
    }
}
