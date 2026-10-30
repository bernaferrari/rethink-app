/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.bernaferrari.bravedns.net.go

import android.content.Context
import com.bernaferrari.bravedns.data.AppConfig
import com.celzero.firestack.intra.Tunnel
import com.celzero.firestack.backend.Backend
import com.bernaferrari.bravedns.database.RethinkDnsEndpoint
import com.bernaferrari.bravedns.util.Constants
import kotlinx.coroutines.CancellationException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GoDnsTransportsTest {

    private fun newTransports(
        tunnel: Tunnel,
        appConfig: AppConfig = mockk(relaxed = true),
        callbacks: GoDnsTransports.Callbacks = mockk(relaxed = true),
        install: (Tunnel, String, String, String) -> Unit = { _, _, _, _ -> error("Unexpected native transport installation") },
    ): GoDnsTransports {
        return GoDnsTransports(
            tunnel = { tunnel },
            context = mockk<Context>(relaxed = true),
            appConfig = appConfig,
            callbacks = callbacks,
            installDoh = install,
        )
    }

    @Test
    fun `removeResolver returns false when tunnel is disconnected`() = runTest {
        val tunnel = mockk<Tunnel>(relaxed = true)
        every { tunnel.isConnected } returns false
        val callbacks = mockk<GoDnsTransports.Callbacks>(relaxed = true)

        val ok = newTransports(tunnel, callbacks = callbacks).removeResolver("preferred")

        assertFalse(ok)
        coVerify(exactly = 0) { callbacks.resolver() }
    }

    @Test
    fun `addTransport is a no-op when tunnel is disconnected`() = runTest {
        val tunnel = mockk<Tunnel>(relaxed = true)
        every { tunnel.isConnected } returns false
        val appConfig = mockk<AppConfig>(relaxed = true)

        newTransports(tunnel, appConfig).addTransport()

        coVerify(exactly = 0) { appConfig.getDnsType() }
    }

    @Test
    fun `addTransport system dns does not add plus resolvers`() = runTest {
        val tunnel = mockk<Tunnel>(relaxed = true)
        every { tunnel.isConnected } returns true
        val appConfig = mockk<AppConfig>(relaxed = true)
        val callbacks = mockk<GoDnsTransports.Callbacks>(relaxed = true)
        every { appConfig.getDnsType() } returns AppConfig.DnsType.SYSTEM_DNS

        newTransports(tunnel, appConfig, callbacks).addTransport()

        coVerify(exactly = 0) { callbacks.addMultipleDnsAsPlus() }
    }

    @Test
    fun `addTransport notifies when preferred doh is missing and installs default`() = runTest {
        val tunnel = mockk<Tunnel>(relaxed = true)
        every { tunnel.isConnected } returns true
        val appConfig = mockk<AppConfig>(relaxed = true)
        val callbacks = mockk<GoDnsTransports.Callbacks>(relaxed = true)
        every { appConfig.getDnsType() } returns AppConfig.DnsType.DOH
        coEvery { appConfig.getDOHDetails() } returns null
        coEvery { appConfig.getRethinkDefaultEndpoint() } returns defaultEndpoint()
        val installed = mutableListOf<String>()

        newTransports(tunnel, appConfig, callbacks, install = { _, id, _, _ -> installed.add(id) }).addTransport()

        assertEquals(listOf(Backend.Preferred), installed)
        io.mockk.verify { callbacks.onDnsFailure(any(), any()) }
        coVerify(exactly = 1) { appConfig.handleRethinkChanges(any()) }
    }

    private fun defaultEndpoint() = RethinkDnsEndpoint(
        "RDNS Default", Constants.BLOCK_FREE_DNS_SKY, -2000, "", false, false, 0, 0, 0,
    )

    @Test
    fun `missing active selection installs preferred before publishing default selection`() = runTest {
        val tunnel = mockk<Tunnel>(relaxed = true)
        every { tunnel.isConnected } returns true
        val appConfig = mockk<AppConfig>(relaxed = true)
        every { appConfig.getDnsType() } returns AppConfig.DnsType.RETHINK_REMOTE
        coEvery { appConfig.getRemoteRethinkEndpoint() } returns null
        coEvery { appConfig.getBlockFreeRethinkEndpoint() } returns Constants.BLOCK_FREE_DNS_SKY
        val endpoint = defaultEndpoint()
        coEvery { appConfig.getRethinkDefaultEndpoint() } returns endpoint
        val events = mutableListOf<String>()
        coEvery { appConfig.handleRethinkChanges(endpoint) } answers { events.add("selection"); Unit }

        newTransports(tunnel, appConfig, install = { _, id, url, _ ->
            assertEquals(endpoint.url, url)
            events.add(id)
        }).addTransport()

        assertEquals(listOf(Backend.BlockFree, Backend.Preferred, "selection"), events)
    }

    @Test
    fun `empty endpoint catalog fails setup instead of reporting successful DNS initialization`() = runTest {
        val tunnel = mockk<Tunnel>(relaxed = true)
        every { tunnel.isConnected } returns true
        val appConfig = mockk<AppConfig>(relaxed = true)
        every { appConfig.getDnsType() } returns AppConfig.DnsType.RETHINK_REMOTE
        coEvery { appConfig.getRemoteRethinkEndpoint() } returns null
        coEvery { appConfig.getBlockFreeRethinkEndpoint() } returns Constants.BLOCK_FREE_DNS_SKY
        coEvery { appConfig.getRethinkDefaultEndpoint() } returns null
        val installed = mutableListOf<String>()

        val failure = runCatching {
            newTransports(tunnel, appConfig, install = { _, id, _, _ -> installed.add(id) }).addTransport()
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf(Backend.BlockFree), installed)
        coVerify(exactly = 0) { appConfig.handleRethinkChanges(any()) }
    }

    @Test
    fun `cancelled default installation cannot publish a new DNS selection`() = runTest {
        val tunnel = mockk<Tunnel>(relaxed = true)
        every { tunnel.isConnected } returns true
        val appConfig = mockk<AppConfig>(relaxed = true)
        every { appConfig.getDnsType() } returns AppConfig.DnsType.RETHINK_REMOTE
        coEvery { appConfig.getRemoteRethinkEndpoint() } returns null
        coEvery { appConfig.getBlockFreeRethinkEndpoint() } returns Constants.BLOCK_FREE_DNS_SKY
        coEvery { appConfig.getRethinkDefaultEndpoint() } returns defaultEndpoint()
        val cancelled = CancellationException("adapter stopped")

        val failure = runCatching {
            newTransports(tunnel, appConfig, install = { _, id, _, _ ->
                if (id == Backend.Preferred) throw cancelled
            }).addTransport()
        }.exceptionOrNull()

        assertTrue(failure === cancelled)
        coVerify(exactly = 0) { appConfig.handleRethinkChanges(any()) }
    }
}
