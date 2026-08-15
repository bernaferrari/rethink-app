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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
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
        callbacks: GoDnsTransports.Callbacks = mockk(relaxed = true)
    ): GoDnsTransports {
        return GoDnsTransports(
            tunnel = { tunnel },
            context = mockk<Context>(relaxed = true),
            appConfig = appConfig,
            callbacks = callbacks
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
    fun `addTransport notifies when preferred doh is missing and falls back`() = runTest {
        val tunnel = mockk<Tunnel>(relaxed = true)
        every { tunnel.isConnected } returns true
        val appConfig = mockk<AppConfig>(relaxed = true)
        val callbacks = mockk<GoDnsTransports.Callbacks>(relaxed = true)
        every { appConfig.getDnsType() } returns AppConfig.DnsType.DOH
        coEvery { appConfig.getDOHDetails() } returns null

        newTransports(tunnel, appConfig, callbacks).addTransport()

        io.mockk.verify { callbacks.onDnsFailure(any(), any()) }
    }
}
