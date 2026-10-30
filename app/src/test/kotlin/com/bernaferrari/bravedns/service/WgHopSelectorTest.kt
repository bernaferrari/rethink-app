/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.database.WgConfigFilesImmutable
import com.bernaferrari.bravedns.service.ProxyManager.ID_WG_BASE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WgHopSelectorTest {

    private val selector = WgHopSelector()

    private fun mapping(
        id: Int,
        active: Boolean = true,
        catchAll: Boolean = false,
        lockdown: Boolean = false,
        metered: Boolean = false,
        ssidEnabled: Boolean = false,
        ssids: String = "",
        name: String = "wg$id",
    ) = WgConfigFilesImmutable(
        id = id,
        name = name,
        configPath = "/wg$id.conf",
        serverResponse = "",
        isActive = active,
        isCatchAll = catchAll,
        isLockdown = lockdown,
        oneWireGuard = false,
        useOnlyOnMetered = metered,
        isDeletable = true,
        ssidEnabled = ssidEnabled,
        ssids = ssids,
    )

    @Test
    fun `unrestricted config is eligible on any network`() {
        assertTrue(
            selector.isEligibleForNetwork(
                id = 1,
                usesMobileNw = true,
                ssid = "",
                mobileOnlySetting = false,
                ssidEnabled = false,
                mappings = listOf(mapping(1)),
            )
        )
    }

    @Test
    fun `mobile-only follows selected cellular even with connected wifi`() {
        val mappings = listOf(mapping(1, metered = true))
        assertTrue(
            selector.isEligibleForNetwork(1, usesMobileNw = true, ssid = "", mobileOnlySetting = true, ssidEnabled = false, mappings = mappings)
        )
        assertTrue(
            selector.isEligibleForNetwork(1, usesMobileNw = true, ssid = "Home", mobileOnlySetting = true, ssidEnabled = false, mappings = mappings)
        )
        assertFalse(
            selector.isEligibleForNetwork(1, usesMobileNw = false, ssid = "", mobileOnlySetting = true, ssidEnabled = false, mappings = mappings)
        )
    }

    @Test
    fun `ssid-based passes only on unmetered when list matches`() {
        val mappings = listOf(mapping(1, ssidEnabled = true, ssids = ""))
        assertTrue(
            selector.isEligibleForNetwork(1, usesMobileNw = false, ssid = "Home", mobileOnlySetting = false, ssidEnabled = true, mappings = mappings)
        )
        assertFalse(
            selector.isEligibleForNetwork(1, usesMobileNw = true, ssid = "", mobileOnlySetting = false, ssidEnabled = true, mappings = mappings)
        )
    }

    @Test
    fun `ssid lookup fail-closed when mapping missing`() {
        assertFalse(selector.matchesSsidListForConfig(99, "Home", emptyList()))
    }

    @Test
    fun `canUseConfig rejects empty id and unknown config`() {
        assertFalse(selector.canUseConfig("", "app", false, "Home", emptyList()))
        assertFalse(selector.canUseConfig(ID_WG_BASE + "1", "app", false, "Home", emptyList()))
    }

    @Test
    fun `canUseConfig allows active eligible mapping`() {
        val mappings = listOf(mapping(1, active = true))
        assertTrue(selector.canUseConfig(ID_WG_BASE + "1", "app(1)", false, "Home", mappings))
    }

    @Test
    fun `canUseConfig rejects inactive non-lockdown mapping`() {
        val mappings = listOf(mapping(1, active = false))
        assertFalse(selector.canUseConfig(ID_WG_BASE + "1", "app(1)", false, "Home", mappings))
    }

    @Test
    fun `lockdown mapping is usable even when inactive`() {
        val mappings = listOf(mapping(1, active = false, lockdown = true))
        assertTrue(selector.canUseConfig(ID_WG_BASE + "1", "app(1)", false, "Home", mappings))
    }

    @Test
    fun `full tunnel one-wg never falls back to direct`() {
        val ids = selector.collectProxyIds(
            uid = 10,
            ip = "1.1.1.1",
            port = 443,
            domain = "example.com",
            usesMobileNw = false,
            ssid = "Home",
            default = "base",
            oneWireGuardId = 7,
            wgProxyIdsForApp = listOf(ID_WG_BASE + "1"),
            mappings = listOf(mapping(1), mapping(7)),
        )
        assertEquals(listOf(ID_WG_BASE + "7"), ids)
    }

    @Test
    fun `split one-wg allows configured default fallback`() {
        val ids = selector.collectProxyIds(10, "1.1.1.1", 443, "example.com", false, "", "base", 7,
            emptyList(), listOf(mapping(7).copy(isSplitProxy = true)))
        assertEquals(listOf(ID_WG_BASE + "7", "base"), ids)
    }

    @Test
    fun `unknown one-wg split status fails closed`() {
        val ids = selector.collectProxyIds(10, "1.1.1.1", 443, "example.com", false, "", "base", 7,
            emptyList(), emptyList())
        assertEquals(listOf(ID_WG_BASE + "7"), ids)
    }

    @Test
    fun `one-wg with invalid id returns empty`() {
        val ids = selector.collectProxyIds(
            uid = 10,
            ip = "1.1.1.1",
            port = 443,
            domain = "example.com",
            usesMobileNw = false,
            ssid = "Home",
            default = "base",
            oneWireGuardId = WireguardManager.INVALID_CONF_ID,
            wgProxyIdsForApp = emptyList(),
            mappings = emptyList(),
        )
        assertTrue(ids.isEmpty())
    }

    @Test
    fun `lockdown short-circuits catch-all collection`() {
        val lockdown = mapping(1, lockdown = true)
        val catchAll = mapping(2, catchAll = true)
        val ids = selector.collectProxyIds(
            uid = 10,
            ip = "1.1.1.1",
            port = 443,
            domain = "example.com",
            usesMobileNw = false,
            ssid = "Home",
            default = "base",
            oneWireGuardId = null,
            wgProxyIdsForApp = listOf(ID_WG_BASE + "1"),
            mappings = listOf(lockdown, catchAll),
        )
        assertEquals(listOf(ID_WG_BASE + "1"), ids)
    }

    @Test
    fun `catch-all is appended after app-specific ids then default`() {
        val app = mapping(1)
        val catchAll = mapping(2, catchAll = true)
        val ids = selector.collectProxyIds(
            uid = 10,
            ip = "1.1.1.1",
            port = 443,
            domain = "example.com",
            usesMobileNw = false,
            ssid = "Home",
            default = "base",
            oneWireGuardId = null,
            wgProxyIdsForApp = listOf(ID_WG_BASE + "1"),
            mappings = listOf(app, catchAll),
        )
        assertEquals(listOf(ID_WG_BASE + "1", ID_WG_BASE + "2", "base"), ids)
    }

    @Test
    fun `empty resolution returns default or empty`() {
        val none = selector.collectProxyIds(
            uid = 10, ip = "1.1.1.1", port = 443, domain = "ex.com",
            usesMobileNw = false, ssid = "Home", default = "",
            oneWireGuardId = null, wgProxyIdsForApp = emptyList(), mappings = emptyList(),
        )
        assertTrue(none.isEmpty())

        val fallback = selector.collectProxyIds(
            uid = 10, ip = "1.1.1.1", port = 443, domain = "ex.com",
            usesMobileNw = false, ssid = "Home", default = "base",
            oneWireGuardId = null, wgProxyIdsForApp = emptyList(), mappings = emptyList(),
        )
        assertEquals(listOf("base"), fallback)
    }

    @Test
    fun `catch-all lockdown omits default`() {
        val catchAll = mapping(2, catchAll = true, lockdown = true)
        val ids = selector.collectProxyIds(
            uid = 10, ip = "1.1.1.1", port = 443, domain = "ex.com",
            usesMobileNw = false, ssid = "Home", default = "base",
            oneWireGuardId = null, wgProxyIdsForApp = emptyList(), mappings = listOf(catchAll),
        )
        assertEquals(listOf(ID_WG_BASE + "2"), ids)
    }

    @Test
    fun `convertStringIdToId strips prefix`() {
        assertEquals(12, selector.convertStringIdToId(ID_WG_BASE + "12"))
        assertEquals(WireguardManager.INVALID_CONF_ID, selector.convertStringIdToId(ID_WG_BASE + "abc"))
    }
    @Test fun `empty SSID allows known wifi without name but cannot infer wifi from noncellular`() {
        val wifi = mapping(1, ssidEnabled = true)
        val id = ID_WG_BASE + 1
        assertTrue(selector.canUseConfig(id, "app", false, "", listOf(wifi), isWifiActive = true))
        assertFalse(selector.canUseConfig(id, "app", false, "", listOf(wifi), isWifiActive = false))
        assertFalse(selector.canUseConfig(id, "app", false, "Home", listOf(wifi), isWifiActive = false))
        val named = wifi.copy(ssids = "[{\"name\":\"Home\",\"type\":\"equal_exact\"}]")
        assertFalse(selector.canUseConfig(id, "app", false, "", listOf(named), isWifiActive = true))
    }

}
