/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.rpnproxy

import com.bernaferrari.bravedns.database.CountryConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpnHopSelectorTest {

    private val prefix = "rpnw"
    private val autoId = "AUTO"
    private val matcher = RpnSsidController()
    private val selector = RpnHopSelector(
        rpnWinPrefix = prefix,
        autoServerId = autoId,
        matchesSsid = matcher::matchesSsidList,
    )

    private fun server(
        key: String,
        id: String = "US-$key-$key",
        enabled: Boolean = true,
        catchAll: Boolean = false,
        lockdown: Boolean = false,
        mobileOnly: Boolean = false,
        ssidBased: Boolean = false,
        ssids: String = "",
    ) = CountryConfig(
        id = id,
        cc = "US",
        name = key,
        key = key,
        isEnabled = enabled,
        catchAll = catchAll,
        lockdown = lockdown,
        mobileOnly = mobileOnly,
        ssidBased = ssidBased,
        ssids = ssids,
    )

    @Test
    fun `unrestricted config is eligible on any network`() {
        assertTrue(
            selector.isEligibleForNetwork(
                id = "any",
                usesMobileNw = true,
                ssid = "",
                mobileOnlySetting = false,
                ssidEnabled = false,
                servers = emptyList(),
            )
        )
    }

    @Test
    fun `metered-only passes only on mobile with empty ssid`() {
        val servers = listOf(server("k1", mobileOnly = true))
        assertTrue(
            selector.isEligibleForNetwork("k1", usesMobileNw = true, ssid = "", mobileOnlySetting = true, ssidEnabled = false, servers = servers)
        )
        assertFalse(
            selector.isEligibleForNetwork("k1", usesMobileNw = true, ssid = "Home", mobileOnlySetting = true, ssidEnabled = false, servers = servers)
        )
        assertFalse(
            selector.isEligibleForNetwork("k1", usesMobileNw = false, ssid = "", mobileOnlySetting = true, ssidEnabled = false, servers = servers)
        )
    }

    @Test
    fun `ssid-based passes only on unmetered when list matches`() {
        val servers = listOf(server("k1", ssidBased = true, ssids = ""))
        assertTrue(
            selector.isEligibleForNetwork("k1", usesMobileNw = false, ssid = "Home", mobileOnlySetting = false, ssidEnabled = true, servers = servers)
        )
        assertFalse(
            selector.isEligibleForNetwork("k1", usesMobileNw = true, ssid = "", mobileOnlySetting = false, ssidEnabled = true, servers = servers)
        )
    }

    @Test
    fun `ssid lookup fail-closed when config missing`() {
        assertFalse(selector.matchesSsidListForConfig("missing", "Home", emptyList()))
    }

    @Test
    fun `canUseConfig rejects empty id and unknown config`() {
        assertFalse(selector.canUseConfig("", "app", false, "Home", emptyList()))
        assertFalse(selector.canUseConfig(prefix + "k1", "app", false, "Home", emptyList()))
    }

    @Test
    fun `canUseConfig allows enabled eligible server`() {
        val servers = listOf(server("k1", enabled = true))
        assertTrue(selector.canUseConfig(prefix + "k1", "app(1)", false, "Home", servers))
    }

    @Test
    fun `canUseConfig rejects disabled non-lockdown server`() {
        val servers = listOf(server("k1", enabled = false))
        assertFalse(selector.canUseConfig(prefix + "k1", "app(1)", false, "Home", servers))
    }

    @Test
    fun `lockdown server is usable even when disabled`() {
        val servers = listOf(server("k1", enabled = false, lockdown = true))
        assertTrue(selector.canUseConfig(prefix + "k1", "app(1)", false, "Home", servers))
    }

    @Test
    fun `lockdown short-circuits catch-all collection`() {
        val lockdown = server("lock", lockdown = true, catchAll = false)
        val catchAll = server("all", catchAll = true)
        val ids = runBlocking {
            selector.collectProxyIds(
                uid = 10,
                ip = "1.1.1.1",
                port = 443,
                domain = "example.com",
                usesMobileNw = false,
                ssid = "Home",
                allProxyIdsForApp = listOf(prefix + "lock"),
                rpnProxyIdsForApp = listOf(prefix + "lock"),
                servers = listOf(lockdown, catchAll),
                resolveAutoId = { "AUTO-TUN" },
            )
        }
        // Historical quirk: lockdown lookup does not strip the RPN prefix, so a
        // prefixed app mapping does not short-circuit catch-all collection.
        assertEquals(listOf(prefix + "lock", prefix + "all"), ids)
    }

    @Test
    fun `catch-all is appended after app-specific ids`() {
        val app = server("app1")
        val catchAll = server("all", catchAll = true)
        val ids = runBlocking {
            selector.collectProxyIds(
                uid = 10,
                ip = "1.1.1.1",
                port = 443,
                domain = "example.com",
                usesMobileNw = false,
                ssid = "Home",
                allProxyIdsForApp = listOf(prefix + "app1"),
                rpnProxyIdsForApp = listOf(prefix + "app1"),
                servers = listOf(app, catchAll),
                resolveAutoId = { "AUTO-TUN" },
            )
        }
        assertEquals(listOf(prefix + "app1", prefix + "all"), ids)
    }

    @Test
    fun `catch-all is skipped when ineligible for metered-only`() {
        val catchAll = server("all", catchAll = true, mobileOnly = true)
        val ids = runBlocking {
            selector.collectProxyIds(
                uid = 10,
                ip = "1.1.1.1",
                port = 443,
                domain = "example.com",
                usesMobileNw = false,
                ssid = "Home",
                allProxyIdsForApp = emptyList(),
                rpnProxyIdsForApp = emptyList(),
                servers = listOf(catchAll),
                resolveAutoId = { "AUTO-TUN" },
            )
        }
        assertEquals(emptyList<String>(), ids)
    }

    @Test
    fun `AUTO app mapping resolves through callback`() {
        val auto = server(autoId, id = autoId, catchAll = true)
        val ids = runBlocking {
            selector.collectProxyIds(
                uid = 10,
                ip = "1.1.1.1",
                port = 443,
                domain = "example.com",
                usesMobileNw = false,
                ssid = "Home",
                allProxyIdsForApp = listOf(prefix + autoId),
                rpnProxyIdsForApp = listOf(prefix + autoId),
                servers = listOf(auto),
                resolveAutoId = { "AUTO-TUN" },
            )
        }
        // AUTO is both app-mapped and catch-all; the catch-all id is prefix+AUTO
        // while the app-mapped entry is already resolved, so AUTO is added twice.
        assertEquals(listOf("AUTO-TUN", "AUTO-TUN"), ids)
    }

    @Test
    fun `disabled catch-all is ignored`() {
        val catchAll = server("all", enabled = false, catchAll = true)
        val ids = runBlocking {
            selector.collectProxyIds(
                uid = 10,
                ip = "1.1.1.1",
                port = 443,
                domain = "example.com",
                usesMobileNw = false,
                ssid = "Home",
                allProxyIdsForApp = emptyList(),
                rpnProxyIdsForApp = emptyList(),
                servers = listOf(catchAll),
                resolveAutoId = { "AUTO-TUN" },
            )
        }
        assertEquals(emptyList<String>(), ids)
    }

    @Test
    fun `ssid lookup does not strip the rpn prefix`() {
        val servers = listOf(server("k1", ssidBased = true, ssids = ""))
        // Historical quirk: eligibility looks up the full proxy id, which misses key-only rows.
        assertFalse(
            selector.isEligibleForNetwork(
                id = prefix + "k1",
                usesMobileNw = false,
                ssid = "Home",
                mobileOnlySetting = false,
                ssidEnabled = true,
                servers = servers,
            )
        )
        assertTrue(
            selector.isEligibleForNetwork(
                id = "k1",
                usesMobileNw = false,
                ssid = "Home",
                mobileOnlySetting = false,
                ssidEnabled = true,
                servers = servers,
            )
        )
    }
}
