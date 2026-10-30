package com.bernaferrari.bravedns.net.go

import com.bernaferrari.bravedns.rpnproxy.RpnSsidController
import com.bernaferrari.bravedns.database.CountryConfig
import com.bernaferrari.bravedns.service.WgSsidMatcher

internal data class ProxyNetworkPolicy(
    val mobileOnly: Boolean, val ssidEnabled: Boolean, val ssids: String,
    private val matchesSsid: (String, String) -> Boolean = { list, name -> WgSsidMatcher.matchesSsidList(list, name, emptyMatchesAll = false) },
) {
    fun allows(mobile: Boolean, wifi: Boolean, ssid: String): Boolean {
        if (!mobileOnly && !ssidEnabled) return true
        if (mobileOnly && mobile) return true
        if (!ssidEnabled || !wifi || mobile) return false
        val emptyPolicy = ssids.isBlank() || emptySsidList.matches(ssids.trim())
        val knownName = ssid.isNotEmpty() && ssid != "<unknown ssid>" && ssid != "\"<unknown ssid>\""
        return emptyPolicy || (knownName && matchesSsid(ssids, ssid))
    }

    companion object {
        private val emptySsidList = Regex("\\[\\s*]")
        private val rpnSsidMatcher = RpnSsidController()
        fun forRpn(config: CountryConfig, auto: CountryConfig?,
            matchesSsid: (String, String) -> Boolean = rpnSsidMatcher::matchesSsidList): ProxyNetworkPolicy? {
            val effective = if (config.hopEnabled) auto ?: return null else config
            return ProxyNetworkPolicy(effective.mobileOnly, effective.ssidBased, effective.ssids, matchesSsid)
        }
    }
}
