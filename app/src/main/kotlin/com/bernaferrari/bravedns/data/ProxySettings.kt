/*
 * Copyright 2020 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.data

import Logger
import Logger.LOG_TAG_VPN
import com.bernaferrari.bravedns.data.AppConfig.ProxyProvider
import com.bernaferrari.bravedns.data.AppConfig.ProxyType
import com.bernaferrari.bravedns.data.AppConfig.TunProxyMode
import com.bernaferrari.bravedns.database.ProxyEndpoint
import com.bernaferrari.bravedns.database.ProxyEndpointRepository
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.TcpProxyHelper
import com.bernaferrari.bravedns.util.OrbotHelper
import kotlinx.coroutines.flow.Flow

interface ProxySettingsApi {
    suspend fun getSocks5ProxyDetails(): ProxyEndpoint?
    suspend fun getHttpProxyDetails(): ProxyEndpoint?
    suspend fun getConnectedOrbotProxy(): ProxyEndpoint?
    suspend fun getOrbotSocks5Endpoint(): ProxyEndpoint?
    suspend fun getOrbotHttpEndpoint(): ProxyEndpoint?
    fun isTcpProxyEnabled(): Boolean
    fun isWireGuardEnabled(): Boolean
    fun addProxy(proxyType: ProxyType, provider: ProxyProvider)
    fun removeAllProxies()
    fun removeProxy(removeType: ProxyType, removeProvider: ProxyProvider)
    fun getProxyType(): String
    fun getProxyProvider(): String
    fun getTunProxyMode(): TunProxyMode
    fun isCustomHttpProxyEnabled(): Boolean
    fun isCustomSocks5Enabled(): Boolean
    fun isOrbotProxyEnabled(): Boolean
    fun isWgEnabled(): Boolean
    fun isProxyEnabled(): Boolean
    fun hasHttpProxyTypeEnabled(): Boolean
    fun hasSocks5ProxyTypeEnabled(): Boolean
    fun canEnableProxy(): Boolean
    fun canEnableSocks5Proxy(): Boolean
    fun canEnableWireguardProxy(): Boolean
    fun canEnableHttpProxy(): Boolean
    fun canEnableTcpProxy(): Boolean
    fun canEnableOrbotProxy(): Boolean
    suspend fun getConnectedSocks5Proxy(): ProxyEndpoint?
    suspend fun getConnectedHttpProxy(): ProxyEndpoint?
    suspend fun updateCustomSocks5Proxy(proxyEndpoint: ProxyEndpoint)
    suspend fun updateOrbotProxy(proxyEndpoint: ProxyEndpoint)
    suspend fun updateCustomHttpProxy(proxyEndpoint: ProxyEndpoint)
    suspend fun updateOrbotHttpProxy(proxyEndpoint: ProxyEndpoint)
    val connectedProxy: Flow<ProxyEndpoint?>
}

class ProxySettings(
    private val persistentState: PersistentState,
    private val proxyEndpointRepository: ProxyEndpointRepository,
    private val firewallSettings: FirewallSettings,
) : ProxySettingsApi {
    private var customSocks5Endpoint: ProxyEndpoint? = null
    private var customHttpEndpoint: ProxyEndpoint? = null
    private var orbotEndpoint: ProxyEndpoint? = null

    private data class ProxySelection(
        val provider: ProxyProvider,
        val type: ProxyType
    )

    private fun proxySelection(): ProxySelection {
        return ProxySelection(
            provider = ProxyProvider.getProxyProvider(persistentState.proxyProvider),
            type = ProxyType.of(persistentState.proxyType)
        )
    }

    private fun canEnableProxyForProviders(vararg allowedProviders: ProxyProvider): Boolean {
        if (!canEnableProxy()) return false
        val provider = proxySelection().provider
        return allowedProviders.contains(provider)
    }

    override suspend fun getSocks5ProxyDetails(): ProxyEndpoint? {
        if (customSocks5Endpoint == null) {
            customSocks5Endpoint = proxyEndpointRepository.getCustomSocks5Endpoint()
        }
        return customSocks5Endpoint
    }

    override suspend fun getHttpProxyDetails(): ProxyEndpoint? {
        if (customHttpEndpoint == null) {
            customHttpEndpoint = proxyEndpointRepository.getHttpProxyDetails()
        }
        return customHttpEndpoint
    }

    override suspend fun getConnectedOrbotProxy(): ProxyEndpoint? {
        if (orbotEndpoint == null) {
            orbotEndpoint = proxyEndpointRepository.getConnectedOrbotProxy()
        }
        return orbotEndpoint
    }

    override suspend fun getOrbotSocks5Endpoint(): ProxyEndpoint? =
        proxyEndpointRepository.getOrbotSocks5Endpoint()

    override suspend fun getOrbotHttpEndpoint(): ProxyEndpoint? =
        proxyEndpointRepository.getOrbotHttpEndpoint()

    override fun isTcpProxyEnabled(): Boolean = TcpProxyHelper.getActiveTcpProxy() != null

    override fun isWireGuardEnabled(): Boolean = proxySelection().type.isProxyTypeWireguard()

    override fun addProxy(proxyType: ProxyType, provider: ProxyProvider) {
        if (proxyType == ProxyType.NONE || provider == ProxyProvider.NONE) {
            removeAllProxies()
            return
        }

        if (provider == ProxyProvider.WIREGUARD ||
            provider == ProxyProvider.ORBOT ||
            provider == ProxyProvider.TCP
        ) {
            setProxy(proxyType, provider)
            return
        }

        val currentProxyType = proxySelection().type
        val nextProxyType =
            when {
                proxyType.isProxyTypeHttp() && currentProxyType.isProxyTypeSocks5() ->
                    ProxyType.HTTP_SOCKS5

                proxyType.isProxyTypeSocks5() && currentProxyType.isProxyTypeHttp() ->
                    ProxyType.HTTP_SOCKS5

                proxyType.isProxyTypeHttp() -> ProxyType.HTTP
                proxyType.isProxyTypeSocks5() -> ProxyType.SOCKS5
                else -> proxyType
            }
        setProxy(nextProxyType, provider)
    }

    override fun removeAllProxies() {
        removeOrbot()
        persistentState.proxyProvider = ProxyProvider.NONE.name
        persistentState.proxyType = ProxyType.NONE.name
        persistentState.updateProxyStatus()
    }

    private fun removeOrbot() {
        OrbotHelper.selectedProxyType = ProxyType.NONE.name
    }

    override fun removeProxy(removeType: ProxyType, removeProvider: ProxyProvider) {
        val currentProxyType = proxySelection().type

        if (currentProxyType.isProxyTypeHttpSocks5()) {
            if (removeType.isProxyTypeHttp()) {
                setProxy(ProxyType.SOCKS5, removeProvider)
                return
            } else if (removeType.isProxyTypeSocks5()) {
                setProxy(ProxyType.HTTP, removeProvider)
                return
            } else {
                Logger.w(
                    LOG_TAG_VPN,
                    "invalid remove proxy call, type: ${removeType.name}, provider: ${removeProvider.name}"
                )
            }
        } else {
            removeAllProxies()
        }
    }

    override fun getProxyType(): String = persistentState.proxyType

    override fun getProxyProvider(): String = persistentState.proxyProvider

    private fun setProxy(type: ProxyType, provider: ProxyProvider) {
        persistentState.proxyProvider = provider.name
        persistentState.proxyType = type.name
        persistentState.updateProxyStatus()
    }

    override fun getTunProxyMode(): TunProxyMode {
        val selection = proxySelection()
        Logger.d(
            LOG_TAG_VPN,
            "selected proxy type: ${selection.type.name}, with provider as ${selection.provider.name}"
        )

        when (selection.provider) {
            ProxyProvider.WIREGUARD -> return TunProxyMode.WIREGUARD
            ProxyProvider.ORBOT -> return TunProxyMode.ORBOT
            else -> Unit
        }

        when (selection.type) {
            ProxyType.HTTP -> return TunProxyMode.HTTPS
            ProxyType.SOCKS5 -> return TunProxyMode.SOCKS5
            ProxyType.HTTP_SOCKS5 -> {
                return TunProxyMode.SOCKS5
            }
            else -> Unit
        }
        return TunProxyMode.NONE
    }

    override fun isCustomHttpProxyEnabled(): Boolean {
        val selection = proxySelection()
        return selection.provider.isProxyProviderCustom() && hasHttpProxyTypeEnabled()
    }

    override fun isCustomSocks5Enabled(): Boolean {
        val selection = proxySelection()
        return selection.provider.isProxyProviderCustom() && hasSocks5ProxyTypeEnabled()
    }

    override fun isOrbotProxyEnabled(): Boolean = proxySelection().provider.isProxyProviderOrbot()

    override fun isWgEnabled(): Boolean = proxySelection().provider.isProxyProviderWireguard()

    override fun isProxyEnabled(): Boolean {
        val selection = proxySelection()
        return !selection.provider.isProxyProviderNone() && selection.type.isAnyProxyEnabled()
    }

    override fun hasHttpProxyTypeEnabled(): Boolean = proxySelection().type.isProxyTypeHasHttp()

    override fun hasSocks5ProxyTypeEnabled(): Boolean = proxySelection().type.isSocks5Enabled()

    override fun canEnableProxy(): Boolean = !firewallSettings.getBraveMode().isDnsMode()

    override fun canEnableSocks5Proxy(): Boolean =
        canEnableProxyForProviders(ProxyProvider.NONE, ProxyProvider.CUSTOM)

    override fun canEnableWireguardProxy(): Boolean =
        canEnableProxyForProviders(ProxyProvider.NONE, ProxyProvider.WIREGUARD)

    override fun canEnableHttpProxy(): Boolean =
        canEnableProxyForProviders(ProxyProvider.NONE, ProxyProvider.CUSTOM)

    override fun canEnableTcpProxy(): Boolean =
        canEnableProxyForProviders(ProxyProvider.NONE, ProxyProvider.TCP)

    override fun canEnableOrbotProxy(): Boolean =
        canEnableProxyForProviders(ProxyProvider.NONE, ProxyProvider.ORBOT)

    override suspend fun getConnectedSocks5Proxy(): ProxyEndpoint? =
        proxyEndpointRepository.getConnectedSocks5Proxy()

    override suspend fun getConnectedHttpProxy(): ProxyEndpoint? =
        proxyEndpointRepository.getConnectedHttpProxy()

    override suspend fun updateCustomSocks5Proxy(proxyEndpoint: ProxyEndpoint) {
        proxyEndpointRepository.update(proxyEndpoint)
        customSocks5Endpoint = proxyEndpoint
        addProxy(ProxyType.SOCKS5, ProxyProvider.CUSTOM)
    }

    override suspend fun updateOrbotProxy(proxyEndpoint: ProxyEndpoint) {
        proxyEndpointRepository.update(proxyEndpoint)
        orbotEndpoint = proxyEndpoint
    }

    override suspend fun updateCustomHttpProxy(proxyEndpoint: ProxyEndpoint) {
        proxyEndpointRepository.update(proxyEndpoint)
        customHttpEndpoint = proxyEndpoint
        addProxy(ProxyType.HTTP, ProxyProvider.CUSTOM)
    }

    override suspend fun updateOrbotHttpProxy(proxyEndpoint: ProxyEndpoint) {
        proxyEndpointRepository.update(proxyEndpoint)
    }

    override val connectedProxy: Flow<ProxyEndpoint?> = proxyEndpointRepository.observeConnectedProxy()

    fun statsTypeName(): String = proxySelection().type.name

    fun statsProviderName(): String = proxySelection().provider.name
}
